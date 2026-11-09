package aradexperience.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/** SES dacă `MAIL_FROM` e setat, altfel emailurile se scriu în log. */
@Slf4j
@Configuration
public class MailConfig {

    @Bean
    MailSender mailSender(MailProps props) {
        if (!props.ses()) {
            log.warn("MAIL_FROM is not set: emails are written to the log instead of being sent (local only)");
            return new LogMailSender();
        }
        return new SesMailSender(SesV2Client.builder().region(Region.of(props.region())).build(), props.from().trim());
    }
}
