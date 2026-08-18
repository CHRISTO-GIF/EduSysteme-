package holyflame.administration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Envoie des SMS via l'API HTTP de Brevo — meme fournisseur et meme cle API que EmailService
 * (app.mail.brevo-api-key), mais endpoint transactionalSMS dedie. Un compte Brevo doit avoir
 * des credits SMS actives (distincts des credits email) pour que l'envoi aboutisse.
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);
    private static final String BREVO_SMS_API_URL = "https://api.brevo.com/v3/transactionalSMS/sms";

    @Value("${app.mail.brevo-api-key:}") private String apiKey;
    @Value("${app.sms.expediteur:EduSystem}") private String expediteur;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean estConfigure() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Envoie un SMS transactionnel. Retourne false si l'API n'est pas configuree ou si l'envoi echoue. */
    public boolean envoyer(String numeroDestinataire, String message) {
        if (!estConfigure()) {
            log.warn("Brevo API non configuree (BREVO_API_KEY vide) — SMS a {} non envoye.", numeroDestinataire);
            return false;
        }
        if (numeroDestinataire == null || numeroDestinataire.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("sender", expediteur);
            body.put("recipient", numeroDestinataire);
            body.put("content", message);
            body.put("type", "transactional");

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BREVO_SMS_API_URL))
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .header("api-key", apiKey)
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return true;
            }
            log.error("Echec de l'envoi de SMS a {} : HTTP {} - {}", numeroDestinataire, response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.error("Echec de l'envoi de SMS a {} : {}", numeroDestinataire, e.getMessage());
            return false;
        }
    }
}
