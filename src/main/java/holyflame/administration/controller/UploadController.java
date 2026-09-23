package holyflame.administration.controller;

import holyflame.administration.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;

@Controller
@RequestMapping("/uploads")
public class UploadController {

    /**
     * Seuls ces types sont affiches directement dans le navigateur. Tout le reste (HTML, SVG,
     * XML, JS...) est servi en telechargement : un fichier HTML/SVG depose par un utilisateur
     * (devoir rendu, piece jointe dont l'extension ne correspond pas au type annonce...) etait
     * sinon execute sur le domaine de l'application quand un autre utilisateur l'ouvrait (XSS).
     */
    private static final Set<String> TYPES_AFFICHABLES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
        "application/pdf", "text/plain",
        "video/mp4", "video/webm", "video/ogg", "audio/mpeg", "audio/ogg", "audio/wav");

    @Autowired private FileStorageService fileStorageService;

    @GetMapping("/**")
    public ResponseEntity<Resource> serve(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI().replaceFirst("/uploads/", "");
        Resource resource = fileStorageService.loadAsResource(path);
        String ct = request.getServletContext().getMimeType(resource.getFilename());
        boolean affichable = ct != null && TYPES_AFFICHABLES.contains(ct);
        ResponseEntity.BodyBuilder reponse = ResponseEntity.ok()
            .contentType(affichable ? MediaType.parseMediaType(ct) : MediaType.APPLICATION_OCTET_STREAM)
            .header("X-Content-Type-Options", "nosniff");
        if (!affichable) {
            reponse.header("Content-Security-Policy", "sandbox")
                .header("Content-Disposition", "attachment; filename=\"" + resource.getFilename() + "\"");
        }
        return reponse.body(resource);
    }
}
