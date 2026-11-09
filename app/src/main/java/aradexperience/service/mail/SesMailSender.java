package aradexperience.service.mail;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

/** Amazon SES (API v2), cu credențialele rolului task-ului ECS. */
@RequiredArgsConstructor
public class SesMailSender implements MailSender {

    private final SesV2Client ses;
    private final String from;

    @Override
    public void send(String to, String subject, String text) {
        ses.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(to).build())
                .content(EmailContent.builder().simple(Message.builder()
                        .subject(utf8(subject))
                        .body(Body.builder().text(utf8(text)).build())
                        .build()).build())
                .build());
    }

    private static Content utf8(String s) {
        return Content.builder().data(s).charset("UTF-8").build();
    }
}
