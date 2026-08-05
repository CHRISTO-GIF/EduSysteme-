package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Controller
@RequestMapping("/rh")
public class RHController {

    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private ContratRepository contratRepository;
    @Autowired private CongeRepository congeRepository;
    @Autowired private SalaireMensuelRepository salaireRepository;
    @Autowired private EtablissementService etablissementService;

    @GetMapping
    public String index() {
        return "redirect:/personnel";
    }

    private Personnel personnelDuMemeEtablissement(Long personnelId) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel p = personnelRepository.findById(personnelId).orElseThrow();
        if (etabId == null || !etabId.equals(p.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Membre du personnel introuvable dans cet établissement.");
        }
        return p;
    }

    // Retrouve la fiche personnel liee au compte de connexion actuellement authentifie
    // (rattachement par email, cf. PersonnelController.creerCompte).
    private Personnel personnelDeLUtilisateurConnecte() {
        Long etabId = etablissementService.getCurrentEtablissementId();
        var u = etablissementService.getCurrentUtilisateur();
        if (u == null || etabId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Utilisateur non authentifie.");
        }
        return personnelRepository.findByEmailAndEtablissementId(u.getEmail(), etabId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Aucune fiche personnel n'est associee a votre compte. Contactez l'administration."));
    }

    // ===== CONTRATS =====
    @PostMapping("/contrats")
    public String ajouterContrat(
            @RequestParam Long personnelId,
            @RequestParam String typeContrat,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam Double salaireBase,
            @RequestParam(required = false) String notes) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        Contrat c = new Contrat();
        c.setPersonnel(personnel);
        c.setTypeContrat(typeContrat); c.setDateDebut(dateDebut); c.setDateFin(dateFin);
        c.setSalaireBase(salaireBase); c.setStatut("ACTIF"); c.setNotes(notes);
        contratRepository.save(c);
        return "redirect:/personnel/" + personnelId + "?saved=true#rh-contrats";
    }

    @PostMapping("/contrats/{id}/supprimer")
    public String supprimerContrat(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = contratRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { contratRepository.delete(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-contrats" : "redirect:/personnel";
    }

    // ===== CONGÉS =====
    @PostMapping("/conges")
    public String ajouterConge(
            @RequestParam Long personnelId,
            @RequestParam String typeConge,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String motif) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        Conge c = new Conge();
        c.setPersonnel(personnel);
        c.setTypeConge(typeConge); c.setDateDebut(dateDebut); c.setDateFin(dateFin);
        c.setJoursNombre((int) ChronoUnit.DAYS.between(dateDebut, dateFin) + 1);
        c.setStatut("EN_ATTENTE"); c.setMotif(motif);
        congeRepository.save(c);
        return "redirect:/personnel/" + personnelId + "?saved=true#rh-conges";
    }

    @PostMapping("/conges/{id}/approuver")
    public String approuverConge(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { c.setStatut("APPROUVE"); congeRepository.save(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-conges" : "redirect:/personnel";
    }

    @PostMapping("/conges/{id}/refuser")
    public String refuserConge(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { c.setStatut("REFUSE"); congeRepository.save(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-conges" : "redirect:/personnel";
    }

    @PostMapping("/conges/{id}/supprimer")
    public String supprimerConge(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { congeRepository.delete(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-conges" : "redirect:/personnel";
    }

    // ===== CONGÉS — auto-service enseignant =====
    // Un enseignant demande son propre conge (la fiche personnel est deduite de son
    // compte connecte, jamais transmise par le client) ; l'ADMIN garde la main pour
    // approuver/refuser via les endpoints ci-dessus.
    @PostMapping("/conges/demander")
    public String demanderConge(
            @RequestParam String typeConge,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String motif) {

        Personnel personnel = personnelDeLUtilisateurConnecte();
        Conge c = new Conge();
        c.setPersonnel(personnel);
        c.setTypeConge(typeConge); c.setDateDebut(dateDebut); c.setDateFin(dateFin);
        c.setJoursNombre((int) ChronoUnit.DAYS.between(dateDebut, dateFin) + 1);
        c.setStatut("EN_ATTENTE"); c.setMotif(motif);
        congeRepository.save(c);
        return "redirect:/tableau-enseignant?saved=true#mes-conges";
    }

    @PostMapping("/conges/{id}/annuler")
    public String annulerConge(@PathVariable Long id) {
        Personnel personnel = personnelDeLUtilisateurConnecte();
        congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && personnel.getId().equals(c.getPersonnel().getId()))
            .filter(c -> "EN_ATTENTE".equals(c.getStatut()))
            .ifPresent(congeRepository::delete);
        return "redirect:/tableau-enseignant?saved=true#mes-conges";
    }

    // ===== SALAIRES =====
    @PostMapping("/salaires")
    public String ajouterSalaire(
            @RequestParam Long personnelId,
            @RequestParam int mois,
            @RequestParam int annee,
            @RequestParam Double salaireBase,
            @RequestParam(defaultValue = "0") Double primes,
            @RequestParam(defaultValue = "0") Double retenues) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        SalaireMensuel s = new SalaireMensuel();
        s.setPersonnel(personnel);
        s.setMois(mois); s.setAnnee(annee); s.setSalaireBase(salaireBase);
        s.setPrimes(primes); s.setRetenues(retenues);
        s.setNet(salaireBase + primes - retenues); s.setStatut("EN_ATTENTE");
        salaireRepository.save(s);
        return "redirect:/personnel/" + personnelId + "?saved=true#rh-salaires";
    }

    @PostMapping("/salaires/{id}/payer")
    public String payerSalaire(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = salaireRepository.findById(id)
            .filter(s -> s.getPersonnel() != null && etabId != null && etabId.equals(s.getPersonnel().getEtablissementId()))
            .map(s -> {
                s.setStatut("PAYE"); s.setDatePaiement(LocalDate.now()); salaireRepository.save(s);
                return s.getPersonnel().getId();
            })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-salaires" : "redirect:/personnel";
    }

    @PostMapping("/salaires/{id}/supprimer")
    public String supprimerSalaire(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = salaireRepository.findById(id)
            .filter(s -> s.getPersonnel() != null && etabId != null && etabId.equals(s.getPersonnel().getEtablissementId()))
            .map(s -> { salaireRepository.delete(s); return s.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-salaires" : "redirect:/personnel";
    }
}
