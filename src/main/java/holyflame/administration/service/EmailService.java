package holyflame.administration.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Autowired private JavaMailSender mailSender;

    @Value("${app.mail.from}") private String from;
    @Value("${app.mail.from-name}") private String fromName;
    @Value("${spring.mail.host:}") private String host;

    /** Envoie un email HTML. Retourne false si le SMTP n'est pas configure ou si l'envoi echoue. */
    public boolean envoyer(String destinataire, String sujet, String corpsHtml) {
        if (host == null || host.isBlank()) {
            log.warn("SMTP non configure (SMTP_HOST vide) — email a {} non envoye.", destinataire);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(destinataire);
            helper.setSubject(sujet);
            helper.setText(corpsHtml, true);
            mailSender.send(message);
            return true;
        } catch (MailException | jakarta.mail.MessagingException | UnsupportedEncodingException e) {
            log.error("Echec de l'envoi d'email a {} : {}", destinataire, e.getMessage());
            return false;
        }
    }
}
