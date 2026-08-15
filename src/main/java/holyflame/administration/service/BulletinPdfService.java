package holyflame.administration.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import holyflame.administration.model.Eleve;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Genere le PDF d'un bulletin cote serveur : rendu HTML statique (pas de Tailwind/JS, pour
 * rester independant d'un navigateur) via un template Thymeleaf dedie, converti en PDF par
 * openhtmltopdf. Les images (logo, photo eleve) sont integrees en base64 pour eviter tout
 * appel reseau pendant la generation.
 */
@Service
public class BulletinPdfService {

    @Autowired
    private TemplateEngine templateEngine;
    @Autowired
    private BulletinService bulletinService;
    @Autowired
    private FileStorageService fileStorageService;

    public byte[] genererPdf(Eleve eleve, Integer trimestre, Long etabId, Map<String, Object> enTeteEtablissement) {
        Map<String, Object> donnees = new LinkedHashMap<>(bulletinService.calculerBulletin(eleve, trimestre, etabId));
        donnees.putAll(enTeteEtablissement);
        donnees.put("trimestre", trimestre);
        donnees.put("logoDataUri", chargerImageEnDataUri((String) donnees.get("logoPath")));
        donnees.put("photoDataUri", chargerImageEnDataUri((String) donnees.get("photoPath")));

        Context contexte = new Context();
        contexte.setVariables(donnees);
        String html = templateEngine.process("bulletin-pdf", contexte);

        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(sortie);
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("Erreur lors de la génération du PDF du bulletin.", e);
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
