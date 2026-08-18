package holyflame.administration.controller;

import holyflame.administration.model.CommunicationMessage;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.ModeleMessage;
import holyflame.administration.model.Personnel;
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
    @Autowired private holyflame.administration.service.EmailService emailService;
    @Autowired private holyflame.administration.service.SmsService smsService;

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
        model.addAttribute("emailConfigure", emailService.estConfigure());
        model.addAttribute("smsConfigure", smsService.estConfigure());
        return "communication";
    }

    /**
     * Envoie reellement le message (email via Brevo, SMS via Brevo) plutot que de simplement
     * marquer ENVOYE sans rien transmettre. Si le fournisseur n'est pas configure (cle API
     * absente), l'envoi echoue proprement et le statut ECHEC reflete la realite.
     */
    @PostMapping("/envoyer")
    public String envoyer(
            @RequestParam String type,
            @RequestParam String sujet,
            @RequestParam String contenu,
            @RequestParam String cibleType,
            @RequestParam(required = false) Long classeId,
            Authentication auth) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        List<String> destinataires;

        if ("TOUS_PARENTS".equals(cibleType)) {
            List<Eleve> eleves = etabId != null ? eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId) : List.of();
            destinataires = extraireContactsEleves(eleves, type);
        } else if ("CLASSE".equals(cibleType) && classeId != null) {
            List<Eleve> eleves = eleveRepository.findByClasseIdOrderByNomAsc(classeId).stream()
                .filter(e -> etabId != null && etabId.equals(e.getEtablissementId())).toList();
            destinataires = extraireContactsEleves(eleves, type);
        } else if ("PERSONNEL".equals(cibleType)) {
            List<Personnel> personnels = etabId != null ? personnelRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId) : List.of();
            destinataires = "SMS".equals(type)
                ? personnels.stream().map(Personnel::getTelephone).filter(s -> s != null && !s.isBlank()).toList()
                : personnels.stream().map(Personnel::getEmail).filter(s -> s != null && !s.isBlank()).toList();
        } else {
            destinataires = List.of();
        }

        int nbReussis = 0;
        for (String destinataire : destinataires) {
            boolean succes = "SMS".equals(type)
                ? smsService.envoyer(destinataire, sujet + " — " + contenu)
                : emailService.envoyer(destinataire, sujet, "<p>" + contenu.replace("\n", "<br>") + "</p>");
            if (succes) nbReussis++;
        }

        CommunicationMessage msg = new CommunicationMessage();
        msg.setType(type); msg.setSujet(sujet); msg.setContenu(contenu);
        msg.setCibleType(cibleType); msg.setDestinataires(String.join(", ", destinataires));
        msg.setNbDestinataires(destinataires.size());
        msg.setExpediteur(auth != null ? auth.getName() : "système");
        msg.setDateEnvoi(horlogeService.maintenant());
        msg.setStatut(nbReussis > 0 ? "ENVOYE" : "ECHEC");
        msg.setEtablissementId(etabId);
        commRepository.save(msg);
        return "redirect:/communication?sent=" + nbReussis;
    }

    private List<String> extraireContactsEleves(List<Eleve> eleves, String type) {
        if ("SMS".equals(type)) {
            return eleves.stream().map(Eleve::getTelephoneParent).filter(s -> s != null && !s.isBlank()).distinct().toList();
        }
        return eleves.stream().map(Eleve::getEmailParent).filter(s -> s != null && !s.isBlank()).distinct().toList();
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
