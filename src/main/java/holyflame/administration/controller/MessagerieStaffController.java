package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.EnseignantAutorisation;
import holyflame.administration.model.MessagePrive;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EnseignantAutorisationRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/messagerie")
public class MessagerieStaffController {

    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private EnseignantAutorisationRepository enseignantAutorisationRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private MessagerieService messagerieService;

    @GetMapping
    public String index(@RequestParam(required = false) String avec, Authentication auth, Model model) {
        String email = auth != null ? auth.getName() : "";
        Utilisateur moi = utilisateurRepository.findByEmail(email).orElse(null);
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null && moi != null && moi.getEtablissement() != null) etabId = moi.getEtablissement().getId();

        List<Eleve> elevesConcernes = new ArrayList<>();
        if (etabId != null) {
            if (moi != null && "ENSEIGNANT".equals(moi.getRole())) {
                List<Long> classeIds = enseignantAutorisationRepository.findByEnseignantIdAndEtablissementId(moi.getId(), etabId).stream()
                    .map(EnseignantAutorisation::getClasseId).distinct().toList();
                elevesConcernes = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId).stream()
                    .filter(e -> e.getClasse() != null && classeIds.contains(e.getClasse().getId()))
                    .toList();
            } else {
                // ADMIN / SECRETAIRE : parents de tous les eleves de l'etablissement
                elevesConcernes = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
            }
        }

        // Regroupement des eleves par email du parent (un parent peut avoir plusieurs enfants)
        Map<String, List<Eleve>> elevesParParent = new LinkedHashMap<>();
        for (Eleve e : elevesConcernes) {
            if (e.getEmailParent() == null || e.getEmailParent().isBlank()) continue;
            elevesParParent.computeIfAbsent(e.getEmailParent(), k -> new ArrayList<>()).add(e);
        }

        Map<String, Map<String, Object>> contactsParEmail = new LinkedHashMap<>();
        for (Map.Entry<String, List<Eleve>> entry : elevesParParent.entrySet()) {
            String nomsEnfants = entry.getValue().stream()
                .map(e -> e.getPrenom() + " " + e.getNom()).distinct().reduce((a, b) -> a + ", " + b).orElse("");
            String classesEnfants = entry.getValue().stream()
                .map(Eleve::getClasse).filter(java.util.Objects::nonNull).map(Classe::getNom).distinct()
                .reduce((a, b) -> a + ", " + b).orElse("");
            contactsParEmail.put(entry.getKey(), messagerieService.creerContact(
                entry.getKey(), "Parent de " + nomsEnfants, classesEnfants.isBlank() ? "Parent" : classesEnfants));
        }

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
        return "messagerie";
    }

    @PostMapping("/envoyer")
    public String envoyer(@RequestParam String destinataire, @RequestParam(required = false) String contenu,
                           @RequestParam(required = false) MultipartFile pieceJointe,
                           Authentication auth, RedirectAttributes ra) throws IOException {
        String email = auth != null ? auth.getName() : "";
        messagerieService.envoyerMessage(email, destinataire, contenu, pieceJointe);
        return "redirect:/messagerie?avec=" + destinataire;
    }
}
