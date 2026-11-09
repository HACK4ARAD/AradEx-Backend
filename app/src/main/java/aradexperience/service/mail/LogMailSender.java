package aradexperience.service.mail;

import lombok.extern.slf4j.Slf4j;

/** Fără SES (MAIL_FROM gol, adică local): emailul se scrie în log, ca fluxul „Am uitat parola” să poată fi încercat. */
@Slf4j
public class LogMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String text) {
        log.info("Email not sent (MAIL_FROM is not set) to {}: {}\n{}", to, subject, text);
    }
}
