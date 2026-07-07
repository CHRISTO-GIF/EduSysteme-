package holyflame.administration.controller;

import holyflame.administration.model.BaremeQuestion;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Examen;
import holyflame.administration.model.Note;
import holyflame.administration.model.NoteQuestion;
import holyflame.administration.repository.BaremeQuestionRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.ExamenRepository;
import holyflame.administration.repository.NoteQuestionRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/examens/{examenId}")
public class CorrectionController {

    @Autowired private ExamenRepository examenRepository;
    @Autowired private BaremeQuestionRepository baremeQuestionRepository;
    @Autowired private NoteQuestionRepository noteQuestionRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private JournalService journalService;

    // ── Configuration du bareme ──────────────────────────────────────────
    @GetMapping("/bareme")
    public String configurerBareme(@PathVariable Long examenId, Model model) {
        Examen examen = examenRepository.findById(examenId).orElseThrow();
        verifierProprietaire(examen);
        List<BaremeQuestion> questions = baremeQuestionRepository.findByExamenIdOrderByOrdreAsc(examenId);
        model.addAttribute("examen", examen);
        model.addAttribute("questions", questions);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "examen-bareme";
    }

    @PostMapping("/bareme")
    public String enregistrerBareme(
            @PathVariable Long examenId,
            @RequestParam List<String> titre,
            @RequestParam List<String> sousTitre,
            @RequestParam List<Double> bareme,
            RedirectAttributes ra) {

        Examen examen = examenRepository.findById(examenId).orElseThrow();
        verifierProprietaire(examen);
        baremeQuestionRepository.deleteByExamenId(examenId);
        for (int i = 0; i < titre.size(); i++) {
            if (titre.get(i) == null || titre.get(i).isBlank()) continue;
            BaremeQuestion q = new BaremeQuestion();
            q.setExamen(examen);
            q.setOrdre(i + 1);
            q.setTitre(titre.get(i));
            q.setSousTitre(i < sousTitre.size() ? sousTitre.get(i) : null);
            q.setBareme(i < bareme.size() ? bareme.get(i) : 0);
            baremeQuestionRepository.save(q);
        }
        ra.addFlashAttribute("successMsg", "Bareme enregistre pour cet examen.");
        return "redirect:/examens/" + examenId + "/correction";
    }

    // ── Correction eleve par eleve ────────────────────────────────────────
    @GetMapping("/correction")
    public String correction(@PathVariable Long examenId, @RequestParam(required = false) Long eleveId, Model model) {
        Examen examen = examenRepository.findById(examenId).orElseThrow();
        verifierProprietaire(examen);
        List<BaremeQuestion> questions = baremeQuestionRepository.findByExamenIdOrderByOrdreAsc(examenId);
        if (questions.isEmpty()) {
            return "redirect:/examens/" + examenId + "/bareme";
        }

        List<Eleve> eleves = examen.getClasse() != null
            ? eleveRepository.findByClasseIdOrderByNomAsc(examen.getClasse().getId())
            : List.of();
        if (eleves.isEmpty()) {
            return "redirect:/examens";
        }

        int index = 0;
        if (eleveId != null) {
            for (int i = 0; i < eleves.size(); i++) {
                if (eleves.get(i).getId().equals(eleveId)) { index = i; break; }
            }
        }
        Eleve eleveActuel = eleves.get(index);

        Map<Long, Double> pointsExistants = new LinkedHashMap<>();
        for (NoteQuestion nq : noteQuestionRepository.findByExamenIdAndEleveId(examenId, eleveActuel.getId())) {
            pointsExistants.put(nq.getBaremeQuestion().getId(), nq.getPoints());
        }

        double totalMax = questions.stream().mapToDouble(q -> q.getBareme() != null ? q.getBareme() : 0).sum();
        double totalActuel = pointsExistants.values().stream().mapToDouble(Double::doubleValue).sum();

        Note noteExistante = trouverNote(examen, eleveActuel);

        model.addAttribute("examen", examen);
        model.addAttribute("questions", questions);
        model.addAttribute("eleves", eleves);
        model.addAttribute("eleveActuel", eleveActuel);
        model.addAttribute("index", index);
        model.addAttribute("pointsExistants", pointsExistants);
        model.addAttribute("totalMax", totalMax);
        model.addAttribute("totalActuel", Math.round(totalActuel * 10) / 10.0);
        model.addAttribute("commentaireExistant", noteExistante != null ? noteExistante.getCommentaire() : null);
        model.addAttribute("eleveIdPrecedent", index > 0 ? eleves.get(index - 1).getId() : null);
        model.addAttribute("eleveIdSuivant", index < eleves.size() - 1 ? eleves.get(index + 1).getId() : null);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "examen-correction";
    }

    @PostMapping("/correction")
    public String enregistrerCorrection(
            @PathVariable Long examenId,
            @RequestParam Long eleveId,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes ra) {

        Examen examen = examenRepository.findById(examenId).orElseThrow();
        verifierProprietaire(examen);
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        List<BaremeQuestion> questions = baremeQuestionRepository.findByExamenIdOrderByOrdreAsc(examenId);

        double total = 0;
        for (BaremeQuestion q : questions) {
            String val = allParams.get("points_" + q.getId());
            double points = 0;
            if (val != null && !val.isBlank()) {
                try { points = Double.parseDouble(val.replace(",", ".")); } catch (NumberFormatException ignored) {}
            }
            double max = q.getBareme() != null ? q.getBareme() : 0;
            if (points < 0) points = 0;
            if (points > max) points = max;
            total += points;

            NoteQuestion nq = noteQuestionRepository.findByBaremeQuestionIdAndEleveId(q.getId(), eleveId).orElseGet(NoteQuestion::new);
            nq.setExamen(examen);
            nq.setEleve(eleve);
            nq.setBaremeQuestion(q);
            nq.setPoints(points);
            noteQuestionRepository.save(nq);
        }

        Note note = trouverNote(examen, eleve);
        if (note == null) note = new Note();
        note.setEleve(eleve);
        note.setMatiere(examen.getMatiere());
        note.setValeur(Math.round(total * 10) / 10.0);
        note.setCoefficient(1.0);
        note.setType("EXAMEN");
        note.setTitre(examen.getMatiere() != null ? "Examen — " + examen.getMatiere().getNom() : "Examen");
        note.setTrimestre(examen.getTrimestre());
        note.setDateEvaluation(examen.getDateExamen());
        note.setCommentaire(allParams.get("commentaire"));
        note.setStatut("PUBLIE");
        note.setSaisieAt(LocalDateTime.now());
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) note.setSaisieParId(utilisateur.getId());
        noteRepository.save(note);

        journalService.log("EXAMEN_CORRIGÉ", "EXAMENS",
            eleve.getNom() + " " + eleve.getPrenom() + " — " + (examen.getMatiere() != null ? examen.getMatiere().getNom() : "?") + " — " + note.getValeur() + "/" + questions.stream().mapToDouble(q -> q.getBareme() != null ? q.getBareme() : 0).sum());

        ra.addFlashAttribute("successMsg", "Note enregistree pour " + eleve.getPrenom() + " " + eleve.getNom() + ".");

        String suivantParam = allParams.get("_suivantId");
        if (suivantParam != null && !suivantParam.isBlank()) {
            return "redirect:/examens/" + examenId + "/correction?eleveId=" + suivantParam;
        }
        return "redirect:/examens/" + examenId + "/correction?eleveId=" + eleveId;
    }

    private void verifierProprietaire(Examen examen) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null || examen.getEtablissementId() == null || !etabId.equals(examen.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Examen introuvable dans cet établissement.");
        }
    }

    private Note trouverNote(Examen examen, Eleve eleve) {
        if (examen.getMatiere() == null || examen.getTrimestre() == null) return null;
        return noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, examen.getTrimestre()).stream()
            .filter(n -> n.getMatiere() != null && n.getMatiere().getId().equals(examen.getMatiere().getId())
                      && Objects.equals(n.getDateEvaluation(), examen.getDateExamen())
                      && "EXAMEN".equals(n.getType()))
            .findFirst().orElse(null);
    }
}
