package aradexperience.service.volunteer;

import aradexperience.dto.media.MediaDto;
import aradexperience.entity.community.CommunityAction;
import aradexperience.entity.users.User;
import aradexperience.entity.volunteer.ActionChatMessage;
import aradexperience.entity.volunteer.ActionChatRead;
import aradexperience.entity.volunteer.Volunteer;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.ForbiddenException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.community.CommunityActionRepository;
import aradexperience.repository.users.UserRepository;
import aradexperience.repository.volunteer.ActionChatMessageRepository;
import aradexperience.repository.volunteer.ActionChatReadRepository;
import aradexperience.repository.volunteer.VolunteerRepository;
import aradexperience.security.CurrentUser;
import aradexperience.service.media.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chatul de grup al voluntarilor (T18): fiecare acțiune are un chat cu toți voluntarii alocați ei — text și poze (pozele în
 * bucket-ul de poze, folderul `chat`, cu nume aleator). Doar voluntarii activi ai acțiunii îl citesc și scriu în el; Primăria nu
 * e în chat. „Citit” = ultimul mesaj văzut, per voluntar și chat (`action_chat_reads`).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VolunteerChatService {

    static final int PAGE = 200;

    private final ActionChatMessageRepository messages;
    private final ActionChatReadRepository reads;
    private final VolunteerRepository volunteers;
    private final CommunityActionRepository actions;
    private final UserRepository users;
    private final MediaService media;
    private final VolunteerAccess access;

    /** Un mesaj + numele autorului. */
    public record MessageView(ActionChatMessage message, String authorName) {}

    /** Un chat din listă: acțiunea, membrii, ultimul mesaj, câte mesaje noi. */
    public record Summary(CommunityAction action, int members, MessageView last, int unread) {}

    /** Un chat deschis: acțiunea, voluntarii ei, mesajele (cronologic). */
    public record Chat(CommunityAction action, List<User> members, List<MessageView> messages) {}

    public List<Summary> chats(CurrentUser current) {
        Volunteer v = access.active(current);
        List<CommunityAction> allocated = access.allocated(v);
        if (allocated.isEmpty()) return List.of();
        List<Long> ids = allocated.stream().map(CommunityAction::getId).toList();
        Map<Long, Long> members = volunteers.members(ids).stream()
                .collect(Collectors.toMap(VolunteerRepository.MembersRow::getActionId, VolunteerRepository.MembersRow::getMembers));
        Map<Long, Long> unread = unread(v.getUserId(), ids);
        Map<Long, ActionChatMessage> last = messages.lastPerAction(ids).stream()
                .collect(Collectors.toMap(ActionChatMessage::getActionId, Function.identity()));
        Map<Long, String> names = names(last.values().stream().map(ActionChatMessage::getAuthorId).toList());
        // chaturile cu mesaje noi întâi, apoi după ultimul mesaj, apoi după ziua acțiunii
        List<Summary> out = new ArrayList<>(allocated.stream().map(a -> {
            ActionChatMessage m = last.get(a.getId());
            return new Summary(a, members.getOrDefault(a.getId(), 0L).intValue(), m == null ? null : new MessageView(m, names.get(m.getAuthorId())),
                    unread.getOrDefault(a.getId(), 0L).intValue());
        }).toList());
        out.sort(java.util.Comparator.comparing((Summary x) -> x.unread() > 0 ? 0 : 1)
                .thenComparing((Summary x) -> x.last() == null ? 0L : -x.last().message().getId())
                .thenComparing((Summary x) -> x.action().getStartAt()));
        return out;
    }

    /** Chatul unei acțiuni; deschiderea îl marchează citit până la ultimul mesaj. */
    @Transactional
    public Chat chat(long actionId, CurrentUser current) {
        Volunteer v = access.active(current);
        CommunityAction a = memberOf(v, actionId);
        List<ActionChatMessage> list = new ArrayList<>(messages.findByActionIdOrderByIdDesc(actionId, PageRequest.of(0, PAGE)));
        Collections.reverse(list);
        if (!list.isEmpty()) markRead(v.getUserId(), actionId, list.get(list.size() - 1).getId());
        List<User> members = users.findAllById(volunteers.findByAction(actionId).stream().map(Volunteer::getUserId).toList()).stream()
                .sorted(java.util.Comparator.comparing(User::getDisplayName)).toList();
        return new Chat(a, members, withNames(list));
    }

    @Transactional
    public MessageView post(long actionId, String text, CurrentUser current) {
        Volunteer v = access.active(current);
        memberOf(v, actionId);
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) throw new BadRequestException("Empty message");
        return save(v, actionId, clean, null);
    }

    /** Poza se verifică după conținut (JPEG / PNG / WebP / GIF, max 10 MB) și se urcă în `chat/` înainte de mesaj. */
    @Transactional
    public MessageView postPhoto(long actionId, byte[] bytes, String caption, CurrentUser current) {
        Volunteer v = access.active(current);
        memberOf(v, actionId);
        String text = caption == null || caption.isBlank() ? null : caption.trim();
        if (text != null && text.length() > 1000) throw new BadRequestException("Caption too long (max 1000)");
        MediaDto photo = media.upload(bytes, "chat");
        return save(v, actionId, text, photo.url());
    }

    /** Câte mesaje noi are voluntarul în toate chaturile lui (punctul de pe tab-ul „Mesaje”). */
    public int unreadTotal(Volunteer v) {
        List<Long> ids = access.allocated(v).stream().map(CommunityAction::getId).toList();
        return ids.isEmpty() ? 0 : unread(v.getUserId(), ids).values().stream().mapToInt(Long::intValue).sum();
    }

    // -------------------------------------------------------------------------------------------------------------

    private CommunityAction memberOf(Volunteer v, long actionId) {
        if (!v.getActionIds().contains(actionId)) throw new ForbiddenException("You are not assigned to this action");
        CommunityAction a = actions.findById(actionId).orElseThrow(() -> NotFoundException.of("Action", actionId));
        if (!VolunteerRules.visible(a)) throw NotFoundException.of("Action", actionId);
        return a;
    }

    private MessageView save(Volunteer v, long actionId, String text, String imageUrl) {
        ActionChatMessage m = new ActionChatMessage();
        m.setActionId(actionId);
        m.setAuthorId(v.getUserId());
        m.setText(text);
        m.setImageUrl(imageUrl);
        m.setCreatedAt(access.now());
        m = messages.save(m);
        markRead(v.getUserId(), actionId, m.getId());   // propriul mesaj nu e „nou” pentru autor
        log.info("Chat message {} in action {} from volunteer {}{}", m.getId(), actionId, v.getUserId(), imageUrl == null ? "" : " (photo)");
        return new MessageView(m, users.findById(v.getUserId()).map(User::getDisplayName).orElse(null));
    }

    private void markRead(long userId, long actionId, long lastId) {
        ActionChatRead r = reads.findById(new ActionChatRead.Key(userId, actionId)).orElseGet(() -> new ActionChatRead(userId, actionId, 0));
        if (lastId > r.getLastReadId()) {
            r.setLastReadId(lastId);
            reads.save(r);
        }
    }

    private Map<Long, Long> unread(long userId, List<Long> actionIds) {
        return messages.unread(userId, actionIds).stream()
                .collect(Collectors.toMap(ActionChatMessageRepository.UnreadRow::getActionId, ActionChatMessageRepository.UnreadRow::getUnread));
    }

    private List<MessageView> withNames(List<ActionChatMessage> list) {
        Map<Long, String> names = names(list.stream().map(ActionChatMessage::getAuthorId).toList());
        return list.stream().map(m -> new MessageView(m, names.get(m.getAuthorId()))).toList();
    }

    private Map<Long, String> names(List<Long> ids) {
        return users.findAllById(ids.stream().filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
    }
}
