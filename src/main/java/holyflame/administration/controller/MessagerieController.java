package holyflame.administration.controller;

import holyflame.administration.model.Eleve;
import holyflame.administration.model.MessagePrive;
import holyflame.administration.model.SignalementMessagerie;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.SignalementMessagerieRepository;
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
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/portail-parent/messages")
public class MessagerieController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private MessagerieService messagerieService;
    @Autowired private SignalementMessagerieRepository signalementMessagerieRepository;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;

    @GetMapping
    public String index(@RequestParam(required = false) String avec, Authentication auth, Model model) {
        String email = auth != null ? auth.getName() : "";
        List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(email);
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null && !enfants.isEmpty()) etabId = enfants.get(0).getEtablissementId();

        // Contacts reels : enseignants des classes de mes enfants + Administration de l'etablissement
        Map<String, Map<String, Object>> contactsParEmail = messagerieService.construireContactsParent(enfants, etabId);

        List<Map<String, Object>> conversations = messagerieService.construireConversations(email, contactsParEmail);

        String correspondantActif = avec;
        if (correspondantActif == null && !conversations.isEmpty()) {
            correspondantActif = (String) conversations.get(0).get("email");
        }

        List<MessagePrive> fil = new java.util.ArrayList<>();
        Map<String, Object> contactActif = null;
        List<Utilisateur> membresGroupe = List.of();
        if (correspondantActif != null) {
            fil = messagerieService.chargerFilEtMarquerLu(email, correspondantActif);
            contactActif = contactsParEmail.get(correspondantActif);
            if (contactActif == null) {
                final String ca = correspondantActif;
                contactActif = conversations.stream()
                    .filter(c -> ca.equalsIgnoreCase((String) c.get("email"))).findFirst().orElse(null);
            }
            if (contactActif != null && "Secretariat".equals(contactActif.get("role")) && etabId != null) {
                membresGroupe = utilisateurRepository.findByEtablissementId(etabId).stream()
                    .filter(u -> "ADMIN".equals(u.getRole()) || "SECRETAIRE".equals(u.getRole()))
                    .toList();
            }
        }

        long totalNonLus = conversations.stream().mapToLong(c -> (Long) c.get("nonLus")).sum();

        model.addAttribute("conversations", conversations);
        model.addAttribute("correspondantActif", correspondantActif);
        model.addAttribute("contactActif", contactActif);
        model.addAttribute("fil", fil);
        model.addAttribute("filAvecSeparateurs", messagerieService.avecSeparateursDate(fil));
        model.addAttribute("mediasPartages", messagerieService.mediasPartages(fil));
        model.addAttribute("membresGroupe", membresGroupe);
        model.addAttribute("totalNonLus", totalNonLus);
        model.addAttribute("emailParent", email);
        return "portail-parent-messagerie";
    }

    @PostMapping("/envoyer")
    public String envoyer(@RequestParam String destinataire, @RequestParam(required = false) String contenu,
                           @RequestParam(required = false) MultipartFile pieceJointe,
                           Authentication auth, RedirectAttributes ra) throws IOException {
        String email = auth != null ? auth.getName() : "";

        // Le destinataire ne peut etre qu'un contact reellement legitime pour ce parent
        // (enseignant d'un de ses enfants, ou administration de son etablissement) — sans ce
        // controle, n'importe quelle adresse email du systeme pourrait recevoir un message.
        List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(email);
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null && !enfants.isEmpty()) etabId = enfants.get(0).getEtablissementId();
        Map<String, Map<String, Object>> contactsParEmail = messagerieService.construireContactsParent(enfants, etabId);
        if (!messagerieService.estContactAutorise(destinataire, contactsParEmail)) {
            ra.addFlashAttribute("erreurMsg", "Ce destinataire n'est pas un contact autorisé.");
            return "redirect:/portail-parent/messages";
        }

        try {
            messagerieService.envoyerMessage(email, destinataire, contenu, pieceJointe, etabId);
        } catch (IOException e) {
            ra.addFlashAttribute("erreurMsg", e.getMessage());
        }
        return "redirect:/portail-parent/messages?avec=" + destinataire;
    }

    @PostMapping("/signaler")
    public String signaler(@RequestParam String concernant, @RequestParam String motif,
                            @RequestParam(required = false) String description,
                            Authentication auth, RedirectAttributes ra) {
        String email = auth != null ? auth.getName() : "";
        SignalementMessagerie s = new SignalementMessagerie();
        s.setSignalePar(email);
        s.setConcernant(concernant);
        s.setMotif(motif);
        s.setDescription(description);
        s.setDateSignalement(horlogeService.maintenant());
        s.setStatut("OUVERT");
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null) {
            List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(email);
            if (!enfants.isEmpty()) etabId = enfants.get(0).getEtablissementId();
        }
        s.setEtablissementId(etabId);
        signalementMessagerieRepository.save(s);
        ra.addFlashAttribute("successMsg", "Votre signalement a ete transmis a l'administration.");
        return "redirect:/portail-parent/messages?avec=" + concernant;
    }
}
