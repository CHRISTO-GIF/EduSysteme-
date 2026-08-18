package holyflame.administration.service;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Integration avec l'API Checkout de CinetPay : un agregateur qui couvre en un seul contrat
 * Orange Money, MTN Mobile Money, Moov Africa et Wave, plutot que de negocier un accord separe
 * avec chaque operateur telecom. Documentation officielle :
 * https://docs.cinetpay.com/api/1.0-fr/checkout/initialisation
 *
 * Regle de securite imposee par CinetPay lui-meme (voir leur documentation) : la notification
 * webhook ne transporte JAMAIS le statut du paiement, uniquement l'identifiant de transaction —
 * pour eviter qu'un tiers rejouant/forgeant l'appel HTTP puisse faire valider un faux paiement.
 * Apres reception de la notification, il faut TOUJOURS rappeler verifierTransaction() pour
 * obtenir le vrai statut aupres de CinetPay avant d'enregistrer quoi que ce soit.
 */
@Service
public class CinetPayService {

    private static final Logger log = LoggerFactory.getLogger(CinetPayService.class);
    private static final String URL_INITIALISATION = "https://api-checkout.cinetpay.com/v2/payment";
    private static final String URL_VERIFICATION = "https://api-checkout.cinetpay.com/v2/payment/check";

    @Value("${app.cinetpay.api-key:}") private String apiKey;
    @Value("${app.cinetpay.site-id:}") private String siteId;
    @Value("${app.mail.base-url}") private String baseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean estConfigure() {
        return apiKey != null && !apiKey.isBlank() && siteId != null && !siteId.isBlank();
    }

    /**
     * Initie un paiement aupres de CinetPay et retourne l'URL de la page de paiement hebergee
     * (l'utilisateur y choisit son operateur : Orange, MTN, Moov, Wave...). Retourne null si
     * CinetPay n'est pas configure ou en cas d'erreur — l'appelant doit prevoir un message clair
     * dans ce cas plutot que de planter.
     */
    public String initierPaiement(String transactionId, double montant, String description) {
        if (!estConfigure()) {
            log.warn("CinetPay non configure (CINETPAY_API_KEY/CINETPAY_SITE_ID absents) — paiement {} non initie.", transactionId);
            return null;
        }
        try {
            Map<String, Object> corps = new HashMap<>();
            corps.put("apikey", apiKey);
            corps.put("site_id", siteId);
            corps.put("transaction_id", transactionId);
            corps.put("amount", Math.round(montant));
            corps.put("currency", "XOF");
            corps.put("description", description);
            corps.put("notify_url", baseUrl + "/paiements/mobile/notification");
            corps.put("return_url", baseUrl + "/portail-parent/paiements");
            corps.put("channels", "ALL");

            HttpRequest requete = HttpRequest.newBuilder()
                .uri(URI.create(URL_INITIALISATION))
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corps)))
                .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            JsonNode racine = objectMapper.readTree(reponse.body());
            if ("201".equals(racine.path("code").asText())) {
                String lien = racine.path("data").path("payment_url").asText(null);
                if (lien != null && !lien.isBlank()) return lien;
            }
            log.error("Echec d'initialisation CinetPay pour la transaction {} : {}", transactionId, reponse.body());
            return null;
        } catch (Exception e) {
            log.error("Echec d'initialisation CinetPay pour la transaction {} : {}", transactionId, e.getMessage());
            return null;
        }
    }

    public static class ResultatVerification {
        public boolean accepte;
        public double montant;
        public String operateur;
    }

    /** Interroge CinetPay pour le vrai statut d'une transaction. Retourne null en cas d'erreur reseau/config. */
    public ResultatVerification verifierTransaction(String transactionId) {
        if (!estConfigure()) return null;
        try {
            Map<String, Object> corps = new HashMap<>();
            corps.put("apikey", apiKey);
            corps.put("site_id", siteId);
            corps.put("transaction_id", transactionId);

            HttpRequest requete = HttpRequest.newBuilder()
                .uri(URI.create(URL_VERIFICATION))
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corps)))
                .build();

            HttpResponse<String> reponse = httpClient.send(requete, HttpResponse.BodyHandlers.ofString());
            JsonNode racine = objectMapper.readTree(reponse.body());

            ResultatVerification resultat = new ResultatVerification();
            String statutDonnees = racine.path("data").path("status").asText("");
            resultat.accepte = "00".equals(racine.path("code").asText()) && "ACCEPTED".equalsIgnoreCase(statutDonnees);
            resultat.montant = racine.path("data").path("amount").asDouble(0);
            resultat.operateur = racine.path("data").path("payment_method").asText(null);
            return resultat;
        } catch (Exception e) {
            log.error("Echec de verification CinetPay pour la transaction {} : {}", transactionId, e.getMessage());
            return null;
        }
    }
}
