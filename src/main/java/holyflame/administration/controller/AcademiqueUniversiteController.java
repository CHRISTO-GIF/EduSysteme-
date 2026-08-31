package holyflame.administration.controller;

import holyflame.administration.model.Departement;
import holyflame.administration.model.Faculte;
import holyflame.administration.model.Filiere;
import holyflame.administration.model.UniteEnseignement;
import holyflame.administration.repository.DepartementRepository;
import holyflame.administration.repository.FaculteRepository;
import holyflame.administration.repository.FiliereRepository;
import holyflame.administration.repository.UniteEnseignementRepository;
import holyflame.administration.service.EtablissementService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Structure academique universitaire (Faculte / Departement / Filiere / Unite d'Enseignement).
 * Departement est facultatif dans la hierarchie : une Filiere peut se rattacher directement
 * a une Faculte, pour les etablissements qui n'ont pas ce niveau intermediaire.
 */
@Controller
@RequestMapping("/academique-universite")
public class AcademiqueUniversiteController {

    @Autowired private FaculteRepository faculteRepository;
    @Autowired private DepartementRepository departementRepository;
    @Autowired private FiliereRepository filiereRepository;
    @Autowired private UniteEnseignementRepository uniteEnseignementRepository;
    @Autowired private EtablissementService etablissementService;

    @GetMapping
    public String index(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("facultes", faculteRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("departements", departementRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("filieres", filiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("unitesEnseignement", uniteEnseignementRepository.findByEtablissementIdOrderBySemestreAscCodeAsc(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "academique-universite";
    }

    // ---- Facultes ----

    @PostMapping("/facultes")
    public String ajouterFaculte(@RequestParam String nom, @RequestParam String code, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (faculteRepository.existsByCodeAndEtablissementId(code.trim(), etabId)) {
            ra.addFlashAttribute("erreur", "Le code \"" + code.trim() + "\" est déjà utilisé par une autre faculté.");
            return "redirect:/academique-universite";
        }
        Faculte f = new Faculte();
        f.setNom(nom.trim());
        f.setCode(code.trim());
        f.setEtablissementId(etabId);
        faculteRepository.save(f);
        ra.addFlashAttribute("success", "Faculté \"" + f.getNom() + "\" créée.");
        return "redirect:/academique-universite";
    }

    @Transactional
    @PostMapping("/facultes/{id}/supprimer")
    public String supprimerFaculte(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Faculte f = faculteRepository.findById(id).orElseThrow();
        verifierProprietaire(f.getEtablissementId(), etabId, "Faculté");
        long nbDepartements = departementRepository.findByFaculteIdAndEtablissementIdOrderByNomAsc(id, etabId).size();
        long nbFilieres = filiereRepository.findByFaculteIdAndEtablissementIdOrderByNomAsc(id, etabId).size();
        if (nbDepartements > 0 || nbFilieres > 0) {
            ra.addFlashAttribute("erreur",
                "Impossible de supprimer cette faculté : elle contient encore des départements ou des filières. Supprimez-les d'abord.");
            return "redirect:/academique-universite";
        }
        faculteRepository.deleteById(id);
        ra.addFlashAttribute("success", "Faculté supprimée.");
        return "redirect:/academique-universite";
    }

    // ---- Departements ----

    @PostMapping("/departements")
    public String ajouterDepartement(@RequestParam String nom, @RequestParam String code,
                                      @RequestParam Long faculteId, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Faculte faculte = faculteRepository.findById(faculteId).orElseThrow();
        verifierProprietaire(faculte.getEtablissementId(), etabId, "Faculté");
        if (departementRepository.existsByCodeAndEtablissementId(code.trim(), etabId)) {
            ra.addFlashAttribute("erreur", "Le code \"" + code.trim() + "\" est déjà utilisé par un autre département.");
            return "redirect:/academique-universite";
        }
        Departement d = new Departement();
        d.setNom(nom.trim());
        d.setCode(code.trim());
        d.setFaculteId(faculteId);
        d.setEtablissementId(etabId);
        departementRepository.save(d);
        ra.addFlashAttribute("success", "Département \"" + d.getNom() + "\" créé.");
        return "redirect:/academique-universite";
    }

    @Transactional
    @PostMapping("/departements/{id}/supprimer")
    public String supprimerDepartement(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Departement d = departementRepository.findById(id).orElseThrow();
        verifierProprietaire(d.getEtablissementId(), etabId, "Département");
        long nbFilieres = filiereRepository.findByDepartementIdAndEtablissementIdOrderByNomAsc(id, etabId).size();
        if (nbFilieres > 0) {
            ra.addFlashAttribute("erreur", "Impossible de supprimer ce département : il contient encore des filières.");
            return "redirect:/academique-universite";
        }
        departementRepository.deleteById(id);
        ra.addFlashAttribute("success", "Département supprimé.");
        return "redirect:/academique-universite";
    }

    // ---- Filieres ----

    @PostMapping("/filieres")
    public String ajouterFiliere(@RequestParam String nom, @RequestParam String code,
                                  @RequestParam(required = false) String niveau,
                                  @RequestParam Long faculteId,
                                  @RequestParam(required = false) Long departementId,
                                  RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Faculte faculte = faculteRepository.findById(faculteId).orElseThrow();
        verifierProprietaire(faculte.getEtablissementId(), etabId, "Faculté");
        if (departementId != null) {
            Departement d = departementRepository.findById(departementId).orElseThrow();
            verifierProprietaire(d.getEtablissementId(), etabId, "Département");
        }
        if (filiereRepository.existsByCodeAndEtablissementId(code.trim(), etabId)) {
            ra.addFlashAttribute("erreur", "Le code \"" + code.trim() + "\" est déjà utilisé par une autre filière.");
            return "redirect:/academique-universite";
        }
        Filiere f = new Filiere();
        f.setNom(nom.trim());
        f.setCode(code.trim());
        f.setNiveau(niveau);
        f.setFaculteId(faculteId);
        f.setDepartementId(departementId);
        f.setEtablissementId(etabId);
        filiereRepository.save(f);
        ra.addFlashAttribute("success", "Filière \"" + f.getNom() + "\" créée.");
        return "redirect:/academique-universite";
    }

    @Transactional
    @PostMapping("/filieres/{id}/supprimer")
    public String supprimerFiliere(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Filiere f = filiereRepository.findById(id).orElseThrow();
        verifierProprietaire(f.getEtablissementId(), etabId, "Filière");
        long nbUe = uniteEnseignementRepository.findByFiliereIdAndEtablissementIdOrderBySemestreAscCodeAsc(id, etabId).size();
        if (nbUe > 0) {
            ra.addFlashAttribute("erreur", "Impossible de supprimer cette filière : elle contient encore des unités d'enseignement.");
            return "redirect:/academique-universite";
        }
        filiereRepository.deleteById(id);
        ra.addFlashAttribute("success", "Filière supprimée.");
        return "redirect:/academique-universite";
    }

    // ---- Unites d'enseignement ----

    @PostMapping("/ue")
    public String ajouterUniteEnseignement(@RequestParam String code, @RequestParam String intitule,
                                            @RequestParam Integer credits, @RequestParam Integer semestre,
                                            @RequestParam Long filiereId, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Filiere filiere = filiereRepository.findById(filiereId).orElseThrow();
        verifierProprietaire(filiere.getEtablissementId(), etabId, "Filière");
        if (uniteEnseignementRepository.existsByCodeAndEtablissementId(code.trim(), etabId)) {
            ra.addFlashAttribute("erreur", "Le code \"" + code.trim() + "\" est déjà utilisé par une autre UE.");
            return "redirect:/academique-universite";
        }
        UniteEnseignement ue = new UniteEnseignement();
        ue.setCode(code.trim());
        ue.setIntitule(intitule.trim());
        ue.setCredits(credits);
        ue.setSemestre(semestre);
        ue.setFiliereId(filiereId);
        ue.setEtablissementId(etabId);
        uniteEnseignementRepository.save(ue);
        ra.addFlashAttribute("success", "Unité d'enseignement \"" + ue.getIntitule() + "\" créée.");
        return "redirect:/academique-universite";
    }

    @PostMapping("/ue/{id}/supprimer")
    public String supprimerUniteEnseignement(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        UniteEnseignement ue = uniteEnseignementRepository.findById(id).orElseThrow();
        verifierProprietaire(ue.getEtablissementId(), etabId, "Unité d'enseignement");
        uniteEnseignementRepository.deleteById(id);
        ra.addFlashAttribute("success", "Unité d'enseignement supprimée.");
        return "redirect:/academique-universite";
    }

    private void verifierProprietaire(Long entiteEtabId, Long etabId, String libelle) {
        if (etabId == null || entiteEtabId == null || !etabId.equals(entiteEtabId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, libelle + " introuvable dans cet établissement.");
        }
    }
}
