package holyflame.administration.controller;

import holyflame.administration.model.MessagePrive;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.MessagerieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/messagerie")
public class MessagerieStaffController {

    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private MessagerieService messagerieService;

    @GetMapping
    public String index(@RequestParam(required = false) String avec, Authentication auth, Model model) {
        String email = auth != null ? auth.getName() : "";
        Utilisateur moi = utilisateurRepository.findByEmail(email).orElse(null);
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null && moi != null && moi.getEtablissement() != null) etabId = moi.getEtablissement().getId();

        Map<String, Map<String, Object>> contactsParEmail = messagerieService.construireContactsStaff(moi, etabId);
        List<Map<String, Object>> conversations = messagerieService.construireConversations(email, contactsParEmail);

        String correspondantActif = avec;
        if (correspondantActif == null && !conversations.isEmpty()) {
            correspondantActif = (String) conversations.get(0).get("email");
        }

        List<MessagePrive> fil = new ArrayList<>();
        Map<String, Object> contactActif = null;
        if (correspondantActif != null) {
            fil = messagerieService.chargerFilEtMarquerLu(email, correspondantActif);
            contactActif = contactsParEmail.get(correspondantActif);
            if (contactActif == null) {
                final String ca = correspondantActif;
                contactActif = conversations.stream()
                    .filter(c -> ca.equalsIgnoreCase((String) c.get("email"))).findFirst().orElse(null);
            }
        }

        model.addAttribute("conversations", conversations);
        model.addAttribute("correspondantActif", correspondantActif);
        model.addAttribute("contactActif", contactActif);
        model.addAttribute("fil", fil);
        model.addAttribute("mediasPartages", messagerieService.mediasPartages(fil));
        model.addAttribute("monEmail", email);
        model.addAttribute("utilisateurConnecte", moi);
        return "messagerie";
    }

    @PostMapping("/envoyer")
    public String envoyer(@RequestParam String destinataire, @RequestParam(required = false) String contenu,
                           @RequestParam(required = false) MultipartFile pieceJointe,
                           Authentication auth, RedirectAttributes ra) throws IOException {
        String email = auth != null ? auth.getName() : "";
        Utilisateur moi = utilisateurRepository.findByEmail(email).orElse(null);
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null && moi != null && moi.getEtablissement() != null) etabId = moi.getEtablissement().getId();

        // Le destinataire ne peut etre que le parent d'un eleve que ce membre du staff peut
        // legitimement contacter (ses classes autorisees pour un enseignant, tout l'etablissement
        // pour admin/secretaire) — sans ce controle, n'importe quelle adresse email du systeme
        // pourrait recevoir un message, y compris d'un autre etablissement.
        Map<String, Map<String, Object>> contactsParEmail = messagerieService.construireContactsStaff(moi, etabId);
        if (!messagerieService.estContactAutorise(destinataire, contactsParEmail)) {
            ra.addFlashAttribute("erreurMsg", "Ce destinataire n'est pas un contact autorisé.");
            return "redirect:/messagerie";
        }

        try {
            messagerieService.envoyerMessage(email, destinataire, contenu, pieceJointe, etabId);
        } catch (IOException e) {
            ra.addFlashAttribute("erreurMsg", e.getMessage());
        }
        return "redirect:/messagerie?avec=" + destinataire;
    }
}
