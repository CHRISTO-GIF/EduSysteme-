package holyflame.administration.controller;

import holyflame.administration.model.Absence;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.AbsenceRepository;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.UtilisateurRepository;
import jakarta.transaction.Transactional;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

@Controller
@RequestMapping("/secretariat")
public class SecretariatController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EtablissementService etablissementService;
    @Autowired private JournalService journalService;

    @GetMapping
    public String index(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        var eleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        var classes = classeRepository.findByEtablissementId(etabId);
        model.addAttribute("eleves",        eleves);
        model.addAttribute("classes",       classes);
        var absences = absenceRepository.findByEtablissementId(etabId);
        model.addAttribute("absences",      absences);
        model.addAttribute("totalAbsences", absences.size());
        model.addAttribute("totalEleves",   eleves.size());
        model.addAttribute("totalClasses",  classes.size());
        model.addAttribute("totalAvecCompte", eleves.stream().filter(e -> e.getCompteEmail() != null).count());
        return "secretariat";
    }

    @PostMapping("/eleves")
    public String ajouterEleve(
            @RequestParam String matricule,
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateNaissance,
            @RequestParam(required = false) String telephoneParent,
            @RequestParam(required = false) String emailParent,
            @RequestParam(required = false) String adresse,
            @RequestParam(required = false) String statutInscription,
            @RequestParam Long classeId) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(classeId).orElseThrow();
        if (!classe.getEtablissementId().equals(etabId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet etablissement.");
        }
        Eleve eleve = new Eleve();
        eleve.setMatricule(matricule);
        eleve.setNom(nom.toUpperCase());
        eleve.setPrenom(prenom);
        eleve.setDateNaissance(dateNaissance);
        eleve.setTelephoneParent(telephoneParent);
        eleve.setEmailParent(emailParent);
        eleve.setAdresse(adresse);
        eleve.setStatutInscription(statutInscription != null ? statutInscription : "INSCRIT");
        eleve.setClasse(classe);
        eleve.setEtablissementId(etablissementService.getCurrentEtablissementId());
        eleveRepository.save(eleve);
        journalService.log("ÉLÈVE_AJOUTÉ", "ELEVES", nom.toUpperCase() + " " + prenom + " — " + matricule);
        return "redirect:/secretariat";
    }

    @GetMapping("/eleves/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(id).orElseThrow();
        verifierProprietaire(eleve, etabId);
        model.addAttribute("eleve", eleve);
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        return "eleve-form";
    }

    @PostMapping("/eleves/{id}/modifier")
    public String modifierEleve(
            @PathVariable Long id,
            @RequestParam String matricule,
            @RequestParam String nom,
            @RequestParam String prenom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateNaissance,
            @RequestParam(required = false) String telephoneParent,
            @RequestParam(required = false) String emailParent,
            @RequestParam(required = false) String adresse,
            @RequestParam(required = false) String statutInscription,
            @RequestParam Long classeId) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(id).orElseThrow();
        verifierProprietaire(eleve, etabId);
        Classe classe = classeRepository.findById(classeId).orElseThrow();
        if (!classe.getEtablissementId().equals(etabId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe introuvable dans cet etablissement.");
        }
        eleve.setMatricule(matricule);
        eleve.setNom(nom.toUpperCase());
        eleve.setPrenom(prenom);
        eleve.setDateNaissance(dateNaissance);
        eleve.setTelephoneParent(telephoneParent);
        eleve.setEmailParent(emailParent);
        eleve.setAdresse(adresse);
        eleve.setStatutInscription(statutInscription);
        eleve.setClasse(classe);
        eleveRepository.save(eleve);
        journalService.log("ÉLÈVE_MODIFIÉ", "ELEVES", nom.toUpperCase() + " " + prenom + " — " + matricule);
        return "redirect:/secretariat";
    }

    @Transactional
    @PostMapping("/eleves/{id}/supprimer")
    public String supprimerEleve(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(id).orElseThrow();
        verifierProprietaire(eleve, etabId);
        journalService.log("ÉLÈVE_SUPPRIMÉ", "ELEVES", eleve.getNom() + " " + eleve.getPrenom());
        noteRepository.deleteByEleveId(id);
        absenceRepository.deleteByEleveId(id);
        paiementRepository.deleteByEleveId(id);
        eleveRepository.deleteById(id);
        return "redirect:/secretariat";
    }

    // ── Créer un compte portail pour un élève ──────────────────────────────
    @PostMapping("/eleves/{id}/creer-compte")
    public String creerCompteEleve(@PathVariable Long id,
                                   @RequestParam String email,
                                   @RequestParam String motDePasse,
                                   RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(id).orElse(null);
        if (eleve == null || !eleve.getEtablissementId().equals(etabId)) {
            ra.addFlashAttribute("erreurMsg", "Élève introuvable.");
            return "redirect:/secretariat";
        }
        if (email == null || email.isBlank()) {
            ra.addFlashAttribute("erreurMsg", "L'adresse email est obligatoire.");
            return "redirect:/secretariat";
        }
        String emailTrim = email.trim().toLowerCase();
        if (utilisateurRepository.findByEmail(emailTrim).isPresent()) {
            ra.addFlashAttribute("erreurMsg",
                "Un compte existe déjà pour l'adresse " + emailTrim + ".");
            return "redirect:/secretariat";
        }
        // Créer le compte Utilisateur
        Utilisateur u = new Utilisateur();
        u.setNom(eleve.getNom());
        u.setPrenom(eleve.getPrenom());
        u.setEmail(emailTrim);
        u.setMotDePasse(passwordEncoder.encode(motDePasse));
        u.setRole("ELEVE");
        u.setEtablissement(etablissementService.getCurrentEtablissement());
        utilisateurRepository.save(u);

        // Lier l'élève à ce compte
        eleve.setCompteEmail(emailTrim);
        eleveRepository.save(eleve);

        ra.addFlashAttribute("successMsg",
            "Compte portail créé pour " + eleve.getNom() + " " + eleve.getPrenom()
            + " — email : " + emailTrim);
        return "redirect:/secretariat";
    }

    // ── Réinitialiser le mot de passe du compte portail d'un élève ───────
    @PostMapping("/eleves/{id}/reset-mdp")
    public String resetMdpEleve(@PathVariable Long id,
                                @RequestParam String motDePasse,
                                RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(id).orElse(null);
        if (eleve == null || eleve.getCompteEmail() == null || !eleve.getEtablissementId().equals(etabId)) {
            ra.addFlashAttribute("erreurMsg", "Élève ou compte introuvable.");
            return "redirect:/secretariat";
        }
        utilisateurRepository.findByEmail(eleve.getCompteEmail()).ifPresentOrElse(u -> {
            u.setMotDePasse(passwordEncoder.encode(motDePasse));
            utilisateurRepository.save(u);
            ra.addFlashAttribute("successMsg",
                "Mot de passe réinitialisé pour " + eleve.getNom() + " " + eleve.getPrenom() + ".");
        }, () -> ra.addFlashAttribute("erreurMsg", "Aucun compte trouvé pour cet élève."));
        return "redirect:/secretariat";
    }

    @PostMapping("/absences")
    public String ajouterAbsence(
            @RequestParam Long eleveId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String periode,
            @RequestParam(required = false) boolean estJustifiee,
            @RequestParam(required = false) String motif) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        verifierProprietaire(eleve, etabId);
        Absence absence = new Absence();
        absence.setEleve(eleve);
        absence.setDate(date);
        absence.setPeriode(periode);
        absence.setEstJustifiee(estJustifiee);
        absence.setMotif(motif);
        absenceRepository.save(absence);
        journalService.log("ABSENCE_SAISIE", "ABSENCES",
            eleve.getNom() + " " + eleve.getPrenom() + " — " + date);
        return "redirect:/secretariat";
    }

    @PostMapping("/absences/{id}/supprimer")
    public String supprimerAbsence(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Absence absence = absenceRepository.findById(id).orElseThrow();
        if (absence.getEleve() != null) verifierProprietaire(absence.getEleve(), etabId);
        absenceRepository.deleteById(id);
        return "redirect:/secretariat";
    }

    /** Verifie que l'eleve appartient bien a l'etablissement de l'utilisateur connecte (isolation multi-tenant). */
    private void verifierProprietaire(Eleve eleve, Long etabId) {
        if (etabId == null || eleve.getEtablissementId() == null || !etabId.equals(eleve.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Élève introuvable dans cet établissement.");
        }
    }
}
