package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Devoir;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.SoumissionDevoir;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.DevoirRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.MatiereRepository;
import holyflame.administration.repository.SoumissionDevoirRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/tableau-enseignant/devoirs")
public class DevoirEnseignantController {

    @Autowired private DevoirRepository devoirRepository;
    @Autowired private SoumissionDevoirRepository soumissionDevoirRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private EtablissementService etablissementService;

    @GetMapping
    public String liste(Model model) {
        Utilisateur moi = etablissementService.getCurrentUtilisateur();
        Long etabId = etablissementService.getCurrentEtablissementId();

        List<Devoir> devoirs = (moi != null && "ENSEIGNANT".equals(moi.getRole()))
            ? devoirRepository.findByEnseignantIdAndEtablissementIdOrderByDateEcheanceAsc(moi.getId(), etabId)
            : devoirRepository.findByEtablissementIdOrderByDateEcheanceAsc(etabId);

        Map<Long, long[]> statsParDevoir = new LinkedHashMap<>(); // [total, termine]
        for (Devoir d : devoirs) {
            List<SoumissionDevoir> soumissions = soumissionDevoirRepository.findByDevoirIdOrderByEleveNomAsc(d.getId());
            long termine = soumissions.stream().filter(s -> "TERMINE".equals(s.getStatut())).count();
            statsParDevoir.put(d.getId(), new long[]{soumissions.size(), termine});
        }

        model.addAttribute("devoirs", devoirs);
        model.addAttribute("statsParDevoir", statsParDevoir);
        model.addAttribute("utilisateurConnecte", moi);
        return "tableau-enseignant-devoirs";
    }

    @GetMapping("/nouveau")
    public String nouveauForm(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "tableau-enseignant-devoir-nouveau";
    }

    @PostMapping
    public String creer(
            @RequestParam String titre,
            @RequestParam(required = false) String description,
            @RequestParam Long classeId,
            @RequestParam(required = false) Long matiereId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEcheance,
            @RequestParam(defaultValue = "MOYENNE") String priorite,
            @RequestParam(required = false) MultipartFile pieceJointe,
            @RequestParam(required = false) boolean publierImmediatement,
            RedirectAttributes ra) throws IOException {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur moi = etablissementService.getCurrentUtilisateur();

        Devoir devoir = new Devoir();
        devoir.setTitre(titre);
        devoir.setDescription(description);
        devoir.setDateEcheance(dateEcheance);
        devoir.setDatePublication(LocalDate.now());
        devoir.setPriorite(priorite);
        devoir.setStatut(publierImmediatement ? "PUBLIE" : "BROUILLON");
        devoir.setEtablissementId(etabId);
        devoir.setEnseignantId(moi != null ? moi.getId() : null);
        classeRepository.findById(classeId).filter(c -> etabId.equals(c.getEtablissementId())).ifPresent(devoir::setClasse);
        if (matiereId != null) {
            matiereRepository.findById(matiereId).filter(m -> etabId.equals(m.getEtablissementId())).ifPresent(devoir::setMatiere);
        }
        if (pieceJointe != null && !pieceJointe.isEmpty()) {
            String chemin = fileStorageService.store(pieceJointe, "devoirs");
            devoir.setPieceJointePath(chemin);
            devoir.setPieceJointeNomOriginal(pieceJointe.getOriginalFilename());
        }
        devoirRepository.save(devoir);

        if ("PUBLIE".equals(devoir.getStatut())) {
            creerSoumissionsManquantes(devoir);
        }

        ra.addFlashAttribute("successMsg", "Devoir \"" + titre + "\" cree avec succes.");
        return "redirect:/tableau-enseignant/devoirs";
    }

    @PostMapping("/{id}/publier")
    public String togglePublier(@PathVariable Long id, RedirectAttributes ra) {
        Devoir devoir = devoirRepository.findById(id).orElseThrow();
        devoir.setStatut("BROUILLON".equals(devoir.getStatut()) ? "PUBLIE" : "BROUILLON");
        devoirRepository.save(devoir);
        if ("PUBLIE".equals(devoir.getStatut())) {
            creerSoumissionsManquantes(devoir);
        }
        return "redirect:/tableau-enseignant/devoirs";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Devoir devoir = devoirRepository.findById(id).orElseThrow();
        List<SoumissionDevoir> soumissions = soumissionDevoirRepository.findByDevoirIdOrderByEleveNomAsc(id);
        model.addAttribute("devoir", devoir);
        model.addAttribute("soumissions", soumissions);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "tableau-enseignant-devoir-detail";
    }

    @PostMapping("/{id}/soumissions/{soumissionId}/corriger")
    public String corriger(@PathVariable Long id, @PathVariable Long soumissionId,
                           @RequestParam(required = false) Double note,
                           @RequestParam(required = false) String appreciation,
                           RedirectAttributes ra) {
        SoumissionDevoir soumission = soumissionDevoirRepository.findById(soumissionId).orElseThrow();
        soumission.setNote(note);
        soumission.setAppreciation(appreciation);
        soumission.setCorrige(true);
        soumission.setDateCorrection(java.time.LocalDateTime.now());
        soumissionDevoirRepository.save(soumission);
        ra.addFlashAttribute("successMsg", "Correction enregistree pour " + soumission.getEleve().getPrenom() + " " + soumission.getEleve().getNom() + ".");
        return "redirect:/tableau-enseignant/devoirs/" + id;
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        Devoir devoir = devoirRepository.findById(id).orElseThrow();
        if (devoir.getPieceJointePath() != null) fileStorageService.delete(devoir.getPieceJointePath());
        soumissionDevoirRepository.findByDevoirIdOrderByEleveNomAsc(id)
            .forEach(s -> { if (s.getFichierPath() != null) fileStorageService.delete(s.getFichierPath()); });
        soumissionDevoirRepository.deleteByDevoirId(id);
        devoirRepository.deleteById(id);
        ra.addFlashAttribute("successMsg", "Devoir supprime.");
        return "redirect:/tableau-enseignant/devoirs";
    }

    /** Cree les lignes de suivi manquantes pour les eleves de la classe qui n'en ont pas encore. */
    private void creerSoumissionsManquantes(Devoir devoir) {
        if (devoir.getClasse() == null) return;
        List<Eleve> eleves = eleveRepository.findByClasseIdOrderByNomAsc(devoir.getClasse().getId());
        for (Eleve e : eleves) {
            if (soumissionDevoirRepository.findByDevoirIdAndEleveId(devoir.getId(), e.getId()).isEmpty()) {
                SoumissionDevoir s = new SoumissionDevoir();
                s.setDevoir(devoir);
                s.setEleve(e);
                soumissionDevoirRepository.save(s);
            }
        }
    }
}
