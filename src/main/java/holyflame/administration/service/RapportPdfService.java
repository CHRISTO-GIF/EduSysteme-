package holyflame.administration.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Genere un rapport PDF a mise en page libre (sections tableau/texte fournies par l'appelant)
 * avec l'en-tete officiel de l'etablissement, sur le meme principe que BulletinPdfService :
 * rendu HTML statique via un template Thymeleaf dedie, converti en PDF par openhtmltopdf.
 */
@Service
public class RapportPdfService {

    @Autowired
    private TemplateEngine templateEngine;
    @Autowired
    private FileStorageService fileStorageService;

    public byte[] genererPdf(String titre, String sousTitre, String periode,
            List<Map<String, Object>> sections, Map<String, Object> enTeteEtablissement) {
        Map<String, Object> donnees = new java.util.LinkedHashMap<>(enTeteEtablissement);
        donnees.put("titreRapport", titre);
        donnees.put("sousTitreRapport", sousTitre);
        donnees.put("periodeRapport", periode);
        donnees.put("sections", sections);
        donnees.put("logoDataUri", chargerImageEnDataUri((String) donnees.get("logoPath")));
        donnees.put("dateGeneration", java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        Context contexte = new Context();
        contexte.setVariables(donnees);
        String html = templateEngine.process("rapport-pdf", contexte);

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(sortie);
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur lors de la génération du PDF du rapport.", e);
        }
        return sortie.toByteArray();
    }

    private String chargerImageEnDataUri(String cheminRelatif) {
        if (cheminRelatif == null || cheminRelatif.isBlank())
            return null;
        try {
            Resource resource = fileStorageService.loadAsResource(cheminRelatif);
            byte[] octets = resource.getInputStream().readAllBytes();
            String mime = mimeDepuisExtension(cheminRelatif);
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(octets);
        } catch (Exception e) {
            return null;
        }
    }

    private String mimeDepuisExtension(String chemin) {
        String c = chemin.toLowerCase();
        if (c.endsWith(".png"))
            return "image/png";
        if (c.endsWith(".svg"))
            return "image/svg+xml";
        if (c.endsWith(".gif"))
            return "image/gif";
        if (c.endsWith(".webp"))
            return "image/webp";
        return "image/jpeg";
    }
}
