package holyflame.administration.controller;

import holyflame.administration.model.CommunicationMessage;
import holyflame.administration.model.ModeleMessage;
import holyflame.administration.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/communication")
public class CommunicationController {

    @Autowired private CommunicationMessageRepository commRepository;
    @Autowired private ModeleMessageRepository modeleRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private holyflame.administration.service.EtablissementService etablissementService;

    @GetMapping
    public String index(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId != null) {
            commRepository.migrateNullEtablissementId(etabId);
            modeleRepository.migrateNullEtablissementId(etabId);
        }
        model.addAttribute("historique", etabId != null ? commRepository.findByEtablissementIdOrderByDateEnvoiDesc(etabId) : List.of());
        model.addAttribute("modeles",    etabId != null ? modeleRepository.findByEtablissementIdOrderByTypeAscNomAsc(etabId) : List.of());
        model.addAttribute("classes",    etabId != null ? classeRepository.findByEtablissementId(etabId) : List.of());
        model.addAttribute("eleves",     etabId != null ? eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId) : List.of());
        model.addAttribute("personnels", etabId != null ? personnelRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId) : List.of());
        model.addAttribute("totalEnvoye", etabId != null ? commRepository.countByStatutAndEtablissementId("ENVOYE", etabId) : 0L);
        return "communication";
    }

    @PostMapping("/envoyer")
    public String envoyer(
            @RequestParam String type,
            @RequestParam String sujet,
            @RequestParam String contenu,
            @RequestParam String cibleType,
            @RequestParam(required = false) Long classeId,
            Authentication auth) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        String destStr;
        int nb;

        if ("TOUS_PARENTS".equals(cibleType)) {
            List<String> emails = (etabId != null ? eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId) : List.<holyflame.administration.model.Eleve>of()).stream()
                .map(e -> e.getEmailParent() != null ? e.getEmailParent() : "")
                .filter(s -> !s.isBlank()).distinct().toList();
            destStr = String.join(", ", emails);
            nb = emails.size();
        } else if ("CLASSE".equals(cibleType) && classeId != null) {
            List<String> emails = eleveRepository.findByClasseIdOrderByNomAsc(classeId).stream()
                .filter(e -> etabId != null && etabId.equals(e.getEtablissementId()))
                .map(e -> e.getEmailParent() != null ? e.getEmailParent() : "")
                .filter(s -> !s.isBlank()).distinct().toList();
            destStr = String.join(", ", emails);
            nb = emails.size();
        } else if ("PERSONNEL".equals(cibleType)) {
            List<String> emails = (etabId != null ? personnelRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId) : List.<holyflame.administration.model.Personnel>of()).stream()
                .map(p -> p.getEmail() != null ? p.getEmail() : "")
                .filter(s -> !s.isBlank()).toList();
            destStr = String.join(", ", emails);
            nb = emails.size();
        } else {
            destStr = "";
            nb = 0;
        }

        CommunicationMessage msg = new CommunicationMessage();
        msg.setType(type); msg.setSujet(sujet); msg.setContenu(contenu);
        msg.setCibleType(cibleType); msg.setDestinataires(destStr);
        msg.setNbDestinataires(nb);
        msg.setExpediteur(auth != null ? auth.getName() : "système");
        msg.setDateEnvoi(horlogeService.maintenant());
        // Simulated send: mark ENVOYE (configure SMTP in application.properties for real emails)
        msg.setStatut(nb > 0 ? "ENVOYE" : "ECHEC");
        msg.setEtablissementId(etabId);
        commRepository.save(msg);
        return "redirect:/communication?sent=" + nb;
    }

    @PostMapping("/modeles")
    public String ajouterModele(
            @RequestParam String nom,
            @RequestParam String type,
            @RequestParam(required = false) String sujet,
            @RequestParam String contenu) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        ModeleMessage m = new ModeleMessage();
        m.setNom(nom); m.setType(type); m.setSujet(sujet); m.setContenu(contenu);
        m.setEtablissementId(etabId);
        modeleRepository.save(m);
        return "redirect:/communication#modeles";
    }

    @PostMapping("/modeles/{id}/supprimer")
    public String supprimerModele(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        modeleRepository.findById(id)
            .filter(m -> etabId != null && etabId.equals(m.getEtablissementId()))
            .ifPresent(modeleRepository::delete);
        return "redirect:/communication#modeles";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimerMessage(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        commRepository.findById(id)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .ifPresent(commRepository::delete);
        return "redirect:/communication";
    }
}
