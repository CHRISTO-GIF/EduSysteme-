package holyflame.administration.controller;

import holyflame.administration.model.Utilisateur;
import holyflame.administration.service.AssistantService;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.RapportCacheService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/assistant")
public class AssistantController {

    @Autowired private AssistantService assistantService;
    @Autowired private EtablissementService etablissementService;
    @Autowired private RapportCacheService rapportCacheService;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("nomEtablissement", nomEtablissementActuel());
        model.addAttribute("assistantConfigure", assistantService.estConfigure());
        return "assistant";
    }

    public static class MessageEntrant {
        public String message;
        public List<AssistantService.Tour> historique;
    }

    public static class MessageSortant {
        public String reponse;
        public String lienRapport;

        public MessageSortant(String reponse, String lienRapport) {
            this.reponse = reponse;
            this.lienRapport = lienRapport;
        }
    }

    @PostMapping("/message")
    @ResponseBody
    public MessageSortant envoyerMessage(@RequestBody MessageEntrant entree) {
        if (entree.message == null || entree.message.isBlank()) {
            return new MessageSortant("Posez une question pour commencer.", null);
        }
        Utilisateur utilisateur = etablissementService.getCurrentUtilisateur();
        // etabId capture ici, cote serveur, a partir de la session authentifiee — jamais
        // envoye par le client ni transmis au modele : c'est la seule source de verite
        // pour le cloisonnement des donnees entre etablissements.
        Long etabId = etablissementService.getCurrentEtablissementId();
        AssistantService.ReponseAssistant reponse = assistantService.repondre(
                entree.message.trim(), entree.historique, utilisateur, etabId, nomEtablissementActuel());
        return new MessageSortant(reponse.texte, reponse.lienRapport);
    }

    /** Telecharge un rapport PDF genere par l'assistant, scope au meme etablissement que celui qui l'a produit. */
    @GetMapping("/rapport/{id}.pdf")
    public void telechargerRapport(@PathVariable String id, HttpServletResponse response) throws IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        byte[] pdf = rapportCacheService.recuperer(id, etabId);
        if (pdf == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=\"rapport.pdf\"");
        response.getOutputStream().write(pdf);
        response.getOutputStream().flush();
    }

    private String nomEtablissementActuel() {
        var etab = etablissementService.getCurrentEtablissement();
        return etab != null ? etab.getNom() : "votre etablissement";
    }
}
