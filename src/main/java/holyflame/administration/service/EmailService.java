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
import java.util.List;
import java.util.Map;

/**
 * Envoie des emails via l'API HTTP de Brevo (port 443) plutot que par SMTP direct :
 * de nombreux hebergeurs cloud bloquent les connexions SMTP sortantes (port 587/465/25),
 * l'API HTTP contourne ce probleme.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    @Value("${app.mail.brevo-api-key:}") private String apiKey;
    @Value("${app.mail.from}") private String from;
    @Value("${app.mail.from-name}") private String fromName;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean estConfigure() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Envoie un email HTML. Retourne false si l'API Brevo n'est pas configuree ou si l'envoi echoue. */
    public boolean envoyer(String destinataire, String sujet, String corpsHtml) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Brevo API non configuree (BREVO_API_KEY vide) — email a {} non envoye.", destinataire);
            return false;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("sender", Map.of("name", fromName, "email", from));
            body.put("to", List.of(Map.of("email", destinataire)));
            body.put("subject", sujet);
            body.put("htmlContent", corpsHtml);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BREVO_API_URL))
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
            log.error("Echec de l'envoi d'email a {} : HTTP {} - {}", destinataire, response.statusCode(), response.body());
            return false;
        } catch (Exception e) {
            log.error("Echec de l'envoi d'email a {} : {}", destinataire, e.getMessage());
            return false;
        }
    }
}
