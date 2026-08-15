package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Devoir;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.SoumissionDevoir;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.DevoirRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EnseignantAutorisationRepository;
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
    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private EtablissementService etablissementService;

    /** Classes que l'enseignant connecte est autorise a enseigner (toutes pour ADMIN/autres roles). */
    private List<Classe> classesAutorisees(Utilisateur moi, Long etabId) {
        List<Classe> toutes = classeRepository.findByEtablissementId(etabId);
        if (moi == null || !"ENSEIGNANT".equals(moi.getRole())) return toutes;
        java.util.Set<Long> mesClasseIds = autorisationRepository.findByEnseignantIdAndEtablissementId(moi.getId(), etabId)
            .stream().map(holyflame.administration.model.EnseignantAutorisation::getClasseId)
            .collect(java.util.stream.Collectors.toSet());
        return toutes.stream().filter(c -> mesClasseIds.contains(c.getId())).toList();
    }

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
        Utilisateur moi = etablissementService.getCurrentUtilisateur();
        model.addAttribute("classes", classesAutorisees(moi, etabId));
        model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("utilisateurConnecte", moi);
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

        // Un enseignant ne peut donner un devoir qu'aux classes qu'il est autorise a enseigner.
        boolean classeAutorisee = classesAutorisees(moi, etabId).stream().anyMatch(c -> c.getId().equals(classeId));
        if (!classeAutorisee) {
            ra.addFlashAttribute("erreurAuth", "Vous n'êtes pas autorisé à donner un devoir pour cette classe.");
            return "redirect:/tableau-enseignant/devoirs/nouveau";
        }

        Devoir devoir = new Devoir();
        devoir.setTitre(titre);
        devoir.setDescription(description);
        devoir.setDateEcheance(dateEcheance);
        devoir.setDatePublication(horlogeService.aujourdHui());
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
        Devoir devoir = trouverDevoirAutorise(id);
        devoir.setStatut("BROUILLON".equals(devoir.getStatut()) ? "PUBLIE" : "BROUILLON");
        devoirRepository.save(devoir);
        if ("PUBLIE".equals(devoir.getStatut())) {
            creerSoumissionsManquantes(devoir);
        }
        return "redirect:/tableau-enseignant/devoirs";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Devoir devoir = trouverDevoirAutorise(id);
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
        trouverDevoirAutorise(id);
        if (note != null && (note < 0 || note > 20)) {
            ra.addFlashAttribute("erreurAuth", "La note doit être comprise entre 0 et 20.");
            return "redirect:/tableau-enseignant/devoirs/" + id;
        }
        SoumissionDevoir soumission = soumissionDevoirRepository.findById(soumissionId)
            .filter(s -> s.getDevoir() != null && id.equals(s.getDevoir().getId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Soumission introuvable pour ce devoir."));
        soumission.setNote(note);
        soumission.setAppreciation(appreciation);
        soumission.setCorrige(true);
        soumission.setDateCorrection(horlogeService.maintenant());
        soumissionDevoirRepository.save(soumission);
        ra.addFlashAttribute("successMsg", "Correction enregistree pour " + soumission.getEleve().getPrenom() + " " + soumission.getEleve().getNom() + ".");
        return "redirect:/tableau-enseignant/devoirs/" + id;
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        Devoir devoir = trouverDevoirAutorise(id);
        if (devoir.getPieceJointePath() != null) fileStorageService.delete(devoir.getPieceJointePath());
        soumissionDevoirRepository.findByDevoirIdOrderByEleveNomAsc(id)
            .forEach(s -> { if (s.getFichierPath() != null) fileStorageService.delete(s.getFichierPath()); });
        soumissionDevoirRepository.deleteByDevoirId(id);
        devoirRepository.deleteById(id);
        ra.addFlashAttribute("successMsg", "Devoir supprime.");
        return "redirect:/tableau-enseignant/devoirs";
    }

    /**
     * Charge un devoir en verifiant qu'il appartient a l'etablissement courant et, pour un
     * enseignant, qu'il en est bien l'auteur — sans ce controle, n'importe quel id de devoir
     * (y compris d'un autre etablissement ou d'un collegue) etait consultable/modifiable.
     */
    private Devoir trouverDevoirAutorise(Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Utilisateur moi = etablissementService.getCurrentUtilisateur();
        Devoir devoir = devoirRepository.findById(id).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Devoir introuvable."));
        if (etabId == null || !etabId.equals(devoir.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Devoir introuvable dans cet établissement.");
        }
        if (moi != null && "ENSEIGNANT".equals(moi.getRole())
                && devoir.getEnseignantId() != null && !devoir.getEnseignantId().equals(moi.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Vous n'êtes pas l'auteur de ce devoir.");
        }
        return devoir;
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
