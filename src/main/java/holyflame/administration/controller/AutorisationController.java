package holyflame.administration.controller;

import holyflame.administration.model.EnseignantAutorisation;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EnseignantAutorisationRepository;
import holyflame.administration.repository.MatiereRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/parametres/autorisations")
public class AutorisationController {

    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private EtablissementService etablissementService;

    @PostMapping("/ajouter")
    public String ajouter(
            @RequestParam Long enseignantId,
            @RequestParam Long matiereId,
            @RequestParam Long classeId,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        boolean enseignantValide = etabId != null && utilisateurRepository.findById(enseignantId)
            .filter(u -> "ENSEIGNANT".equals(u.getRole()) && u.getEtablissement() != null
                && etabId.equals(u.getEtablissement().getId()))
            .isPresent();
        boolean matiereValide = etabId != null && matiereRepository.findById(matiereId)
            .filter(m -> etabId.equals(m.getEtablissementId())).isPresent();
        boolean classeValide = etabId != null && classeRepository.findById(classeId)
            .filter(c -> etabId.equals(c.getEtablissementId())).isPresent();

        if (!enseignantValide || !matiereValide || !classeValide) {
            ra.addFlashAttribute("autorisationMsg", "Enseignant, matière ou classe introuvable dans cet établissement.");
            return "redirect:/parametres#config-autorisations";
        }

        if (!autorisationRepository.existsByEnseignantIdAndMatiereIdAndClasseId(enseignantId, matiereId, classeId)) {
            EnseignantAutorisation a = new EnseignantAutorisation();
            a.setEnseignantId(enseignantId);
            a.setMatiereId(matiereId);
            a.setClasseId(classeId);
            a.setEtablissementId(etabId);
            autorisationRepository.save(a);
            ra.addFlashAttribute("autorisationMsg", "Autorisation ajoutée.");
        } else {
            ra.addFlashAttribute("autorisationMsg", "Cette autorisation existe déjà.");
        }
        return "redirect:/parametres#config-autorisations";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        autorisationRepository.findById(id)
            .filter(a -> etabId != null && etabId.equals(a.getEtablissementId()))
            .ifPresent(autorisationRepository::delete);
        ra.addFlashAttribute("autorisationMsg", "Autorisation retirée.");
        return "redirect:/parametres#config-autorisations";
    }
}
