package aradexperience.service.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Trimite emailul pe un fir separat, după commit: cererea HTTP nu așteaptă după SES (deci durează la fel și când adresa nu
 * are cont), iar un email nu pleacă pentru un cod care n-a ajuns în baza de date. O eroare de trimitere doar se loghează.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailOutbox {

    private final MailSender sender;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mail");
        t.setDaemon(true);
        return t;
    });

    public void sendAfterCommit(String to, String subject, String text) {
        Runnable send = () -> executor.execute(() -> {
            try {
                sender.send(to, subject, text);
            } catch (RuntimeException e) {
                log.error("Email to {} failed: {}", mask(to), e.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    /** În log doar prima literă și domeniul. */
    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
