package aradexperience.repository.tickets;

import aradexperience.entity.tickets.Ticket;
import aradexperience.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByCode(String code);

    List<Ticket> findByUserIdAndStatusNot(Long userId, TicketStatus status);

    boolean existsByUserIdAndEventIdAndStatusNot(Long userId, Long eventId, TicketStatus status);

    /** V16: aceeași plată Stripe nu emite două bilete. */
    boolean existsByPaymentIntentId(String paymentIntentId);

    /** T25: biletul deja emis pentru o plată (webhook-ul și aplicația pot cere amândouă biletul). */
    java.util.Optional<Ticket> findByPaymentIntentId(String paymentIntentId);
}
