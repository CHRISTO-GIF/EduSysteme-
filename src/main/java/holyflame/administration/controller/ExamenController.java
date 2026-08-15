package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Examen;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Note;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EnseignantAutorisationRepository;
import holyflame.administration.repository.ExamenRepository;
import holyflame.administration.repository.MatiereRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/examens")
public class ExamenController {

    @Autowired private ExamenRepository examenRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private JournalService journalService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;

    private boolean isEnseignant() {
        holyflame.administration.model.Utilisateur u = etablissementService.getCurrentUtilisateur();
        return u != null && "ENSEIGNANT".equals(u.getRole());
    }

    /** Un enseignant ne peut planifier/supprimer un examen que pour une matiere+classe qu'il enseigne. */
    private boolean isAutorise(Long matiereId, Long classeId) {
        if (!isEnseignant()) return true; // ADMIN/SECRETAIRE : acces total
        holyflame.administration.model.Utilisateur u = etablissementService.getCurrentUtilisateur();
        Long etabId = etablissementService.getCurrentEtablissementId();
        return autorisationRepository.findByEnseignantIdAndEtablissementId(u.getId(), etabId).stream()
            .anyMatch(a -> a.getMatiereId().equals(matiereId) && a.getClasseId().equals(classeId));
    }

    @GetMapping
    public String index(@RequestParam(required = false) Long classeId, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());

        List<Classe> classes = classeRepository.findByEtablissementId(etabId);
        List<Matiere> matieres = matiereRepository.findByEtablissementIdOrderByNomAsc(etabId);
        model.addAttribute("classes", classes);
        model.addAttribute("matieres", matieres);
        model.addAttribute("classeFiltre", classeId);

        List<Examen> examens = classeId != null
            ? examenRepository.findByEtablissementIdAndClasseIdOrderByDateExamenAscHeureDebutAsc(etabId, classeId)
            : examenRepository.findByEtablissementIdOrderByDateExamenAscHeureDebutAsc(etabId);

        LocalDate aujourdHui = horlogeService.aujourdHui();
        LocalTime maintenant = horlogeService.maintenant().toLocalTime();

        List<Map<String, Object>> sessions = new ArrayList<>();
        for (Examen e : examens) {
            String statut = calculerStatut(e, aujourdHui, maintenant);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("examen", e);
            row.put("statut", statut);
            sessions.add(row);
        }
        model.addAttribute("sessions", sessions);

        long examensEnCours = examenRepository.countByEtablissementIdAndDateExamenGreaterThanEqual(etabId, aujourdHui);
        model.addAttribute("examensEnCours", examensEnCours);

        List<Note> notesExamen = noteRepository.findByEtablissementIdAndType(etabId, "EXAMEN");
        List<Note> notesPubliees = notesExamen.stream().filter(n -> "PUBLIE".equals(n.getStatut())).toList();
        List<Note> notesBrouillon = notesExamen.stream().filter(n -> "BROUILLON".equals(n.getStatut())).toList();

        double moyenneGenerale = notesPubliees.stream().mapToDouble(Note::getValeur).average().orElse(0);
        model.addAttribute("moyenneGenerale", Math.round(moyenneGenerale * 10) / 10.0);

        long elevesAyantNote = notesExamen.stream().map(n -> n.getEleve().getId()).distinct().count();
        long totalEleves = eleveRepository.countByEtablissementId(etabId);
        double tauxParticipation = totalEleves > 0 ? Math.round(elevesAyantNote * 1000.0 / totalEleves) / 10.0 : 0;
        model.addAttribute("tauxParticipation", tauxParticipation);

        model.addAttribute("copiesACorriger", notesBrouillon.size());

        // Derniers resultats par matiere (notes publiees uniquement)
        Map<String, List<Note>> parMatiere = notesPubliees.stream()
            .collect(Collectors.groupingBy(n -> n.getMatiere().getNom()));
        List<Map<String, Object>> derniersResultats = new ArrayList<>();
        for (Map.Entry<String, List<Note>> entry : parMatiere.entrySet()) {
            List<Note> notes = entry.getValue();
            LocalDate derniereDate = notes.stream().map(Note::getDateEvaluation)
                .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
            double min = notes.stream().mapToDouble(Note::getValeur).min().orElse(0);
            double max = notes.stream().mapToDouble(Note::getValeur).max().orElse(0);
            double moy = notes.stream().mapToDouble(Note::getValeur).average().orElse(0);

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("matiere", entry.getKey());
            ligne.put("derniereDate", derniereDate);
            ligne.put("min", Math.round(min * 10) / 10.0);
            ligne.put("max", Math.round(max * 10) / 10.0);
            ligne.put("moyenne", Math.round(moy * 10) / 10.0);
            derniersResultats.add(ligne);
        }
        derniersResultats.sort((a, b) -> {
            LocalDate da = (LocalDate) a.get("derniereDate");
            LocalDate db = (LocalDate) b.get("derniereDate");
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });
        model.addAttribute("derniersResultats", derniersResultats.stream().limit(3).toList());

        // Action prioritaire : combinaison matiere+classe avec le plus de copies en attente
        Map<String, Long> brouillonParGroupe = notesBrouillon.stream()
            .collect(Collectors.groupingBy(
                n -> n.getMatiere().getNom() + " (" + (n.getEleve().getClasse() != null ? n.getEleve().getClasse().getNom() : "?") + ")",
                Collectors.counting()));
        String groupePrioritaire = brouillonParGroupe.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse(null);
        Long nbCopiesPrioritaire = groupePrioritaire != null ? brouillonParGroupe.get(groupePrioritaire) : 0L;
        model.addAttribute("groupePrioritaire", groupePrioritaire);
        model.addAttribute("nbCopiesPrioritaire", nbCopiesPrioritaire);

        return "examens";
    }

    private String calculerStatut(Examen e, LocalDate aujourdHui, LocalTime maintenant) {
        if (e.getDateExamen() == null) return "PLANIFIE";
        if (e.getDateExamen().isBefore(aujourdHui)) return "TERMINE";
        if (e.getDateExamen().isEqual(aujourdHui)) {
            LocalTime hd = parseHeure(e.getHeureDebut());
            LocalTime hf = parseHeure(e.getHeureFin());
            if (hd != null && hf != null && !maintenant.isBefore(hd) && !maintenant.isAfter(hf)) return "LIVE";
            if (hd != null && maintenant.isBefore(hd)) return "A_VENIR";
            return "TERMINE";
        }
        long joursRestants = aujourdHui.until(e.getDateExamen()).getDays();
        return joursRestants <= 3 ? "A_VENIR" : "EN_PREPARATION";
    }

    private LocalTime parseHeure(String heure) {
        if (heure == null || heure.isBlank()) return null;
        try {
            return LocalTime.parse(heure, DateTimeFormatter.ofPattern("H:mm"));
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping
    public String planifier(
            @RequestParam Long matiereId,
            @RequestParam Long classeId,
            @RequestParam(required = false) String salle,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateExamen,
            @RequestParam(required = false) String heureDebut,
            @RequestParam(required = false) String heureFin,
            @RequestParam Integer trimestre,
            @RequestParam(required = false, defaultValue = "false") boolean confirmerQuota,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Matiere matiere = matiereRepository.findById(matiereId).orElseThrow();
        Classe classe = classeRepository.findById(classeId).orElseThrow();
        if (!etabId.equals(matiere.getEtablissementId()) || !etabId.equals(classe.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Matiere ou classe introuvable dans cet etablissement.");
        }
        if (!isAutorise(matiereId, classeId)) {
            ra.addFlashAttribute("erreurAuth", "Vous n'êtes pas autorisé à planifier un examen pour cette matière ou classe.");
            return "redirect:/examens";
        }

        // Un seul examen est normalement autorise par matiere et par trimestre pour une classe.
        if (!confirmerQuota && examenRepository.countByMatiereIdAndClasseIdAndTrimestre(matiereId, classeId, trimestre) >= 1) {
            ra.addFlashAttribute("erreurAuth",
                "Un examen de " + matiere.getNom() + " est déjà planifié pour la classe " + classe.getNom()
                    + " ce trimestre — un seul examen est normalement autorisé. Cochez la confirmation pour en planifier un second.");
            return "redirect:/examens";
        }

        Examen examen = new Examen();
        examen.setMatiere(matiere);
        examen.setClasse(classe);
        examen.setSalle(salle);
        examen.setDateExamen(dateExamen);
        examen.setHeureDebut(heureDebut);
        examen.setHeureFin(heureFin);
        examen.setTrimestre(trimestre);
        examen.setEtablissementId(etabId);
        examenRepository.save(examen);

        journalService.log("EXAMEN_PLANIFIÉ", "EXAMENS",
            matiere.getNom() + " — " + classe.getNom() + " — " + dateExamen);
        ra.addFlashAttribute("successMsg", "Examen de " + matiere.getNom() + " planifie pour la classe " + classe.getNom() + ".");
        return "redirect:/examens";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Examen examen = examenRepository.findById(id)
            .filter(e -> etabId.equals(e.getEtablissementId()))
            .orElse(null);
        if (examen == null) {
            ra.addFlashAttribute("erreurAuth", "Examen introuvable.");
            return "redirect:/examens";
        }
        Long matiereId = examen.getMatiere() != null ? examen.getMatiere().getId() : null;
        Long classeId = examen.getClasse() != null ? examen.getClasse().getId() : null;
        if (!isAutorise(matiereId, classeId)) {
            ra.addFlashAttribute("erreurAuth", "Vous n'êtes pas autorisé à supprimer cet examen.");
            return "redirect:/examens";
        }
        journalService.log("EXAMEN_SUPPRIMÉ", "EXAMENS",
            (examen.getMatiere() != null ? examen.getMatiere().getNom() : "?") + " — " + examen.getDateExamen());
        examenRepository.delete(examen);
        ra.addFlashAttribute("successMsg", "Examen supprime.");
        return "redirect:/examens";
    }
}
