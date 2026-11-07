package aradexperience.repository.tickets;

import aradexperience.entity.tickets.Ticket;
import aradexperience.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByCode(String code);

    /** T25: biletul deja emis pentru un PaymentIntent Stripe (confirmarea și webhook-ul nu dau două bilete). */
    Optional<Ticket> findByPaymentIntentId(String paymentIntentId);

    List<Ticket> findByUserIdAndStatusNot(Long userId, TicketStatus status);

    boolean existsByUserIdAndEventIdAndStatusNot(Long userId, Long eventId, TicketStatus status);
}
