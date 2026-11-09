package aradexperience.service.mail;

/** Trimite un email text simplu: Amazon SES în producție, log local. */
public interface MailSender {

    void send(String to, String subject, String text);
}
