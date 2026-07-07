package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/notes")
public class NoteController {

    @Autowired private NoteRepository noteRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private JournalService journalService;

    // ──────────────────────────────────────────────────────────────
    // Helpers pour les autorisations enseignant
    // ──────────────────────────────────────────────────────────────

    private boolean isEnseignant() {
        Utilisateur u = etablissementService.getCurrentUtilisateur();
        return u != null && "ENSEIGNANT".equals(u.getRole());
    }

    /** Liste des autorisations du teacher connecté */
    private List<EnseignantAutorisation> getMyAutorisations() {
        Utilisateur u = etablissementService.getCurrentUtilisateur();
        if (u == null) return List.of();
        Long etabId = etablissementService.getCurrentEtablissementId();
        return autorisationRepository.findByEnseignantIdAndEtablissementId(u.getId(), etabId);
    }

    /** Vérifie que le teacher a le droit de saisir pour cette matière + classe */
    private boolean isAutorise(Long matiereId, Long classeId) {
        if (!isEnseignant()) return true; // ADMIN ou SECRETAIRE : accès total
        return getMyAutorisations().stream()
            .anyMatch(a -> a.getMatiereId().equals(matiereId) && a.getClasseId().equals(classeId));
    }

    // ──────────────────────────────────────────────────────────────
    // Vue liste des notes
    // ──────────────────────────────────────────────────────────────

    @GetMapping
    public String index(
            @RequestParam(required = false) Integer trimestre,
            @RequestParam(required = false) Long classeId,
            Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Note> notes = noteRepository.findByEtablissementId(etabId);
        if (trimestre != null) {
            final int t = trimestre;
            notes = notes.stream().filter(n -> t == n.getTrimestre()).collect(Collectors.toList());
        }
        notes.sort((a, b) -> b.getDateEvaluation().compareTo(a.getDateEvaluation()));

        // Enseignant : filtrer les notes de ses classes autorisées
        if (isEnseignant()) {
            Set<Long> myClasseIds = getMyAutorisations().stream()
                .map(EnseignantAutorisation::getClasseId).collect(Collectors.toSet());
            notes = notes.stream()
                .filter(n -> n.getEleve() != null && n.getEleve().getClasse() != null
                          && myClasseIds.contains(n.getEleve().getClasse().getId()))
                .collect(Collectors.toList());
        }

        model.addAttribute("notes", notes);
        model.addAttribute("eleves",   eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId));
        model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("classes",  classeRepository.findByEtablissementId(etabId));
        model.addAttribute("trimestreFiltre", trimestre);
        return "notes";
    }

    @PostMapping
    public String ajouterNote(
            @RequestParam Long eleveId,
            @RequestParam Long matiereId,
            @RequestParam Double valeur,
            @RequestParam(required = false) Double coefficient,
            @RequestParam String type,
            @RequestParam Integer trimestre,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEvaluation,
            @RequestParam(required = false) String commentaire,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        Matiere matiere = matiereRepository.findById(matiereId).orElseThrow();
        if (etabId == null || !etabId.equals(eleve.getEtablissementId()) || !etabId.equals(matiere.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Élève ou matière introuvable dans cet établissement.");
        }
        // Sécurité : vérifier que l'enseignant est autorisé
        if (isEnseignant() && !isAutorise(matiereId, eleve.getClasse().getId())) {
            ra.addFlashAttribute("erreurAuth", "Vous n'êtes pas autorisé pour cette matière ou classe.");
            return "redirect:/notes";
        }
        if (valeur == null || valeur < 0 || valeur > 20) {
            ra.addFlashAttribute("erreurAuth", "La note doit être comprise entre 0 et 20.");
            return "redirect:/notes";
        }

        Note note = new Note();
        note.setEleve(eleve); note.setMatiere(matiere);
        note.setValeur(valeur);
        note.setCoefficient(coefficient != null ? coefficient : matiere.getCoefficient());
        note.setType(type); note.setTrimestre(trimestre);
        note.setDateEvaluation(dateEvaluation != null ? dateEvaluation : LocalDate.now());
        note.setCommentaire(commentaire);
        note.setSaisieAt(LocalDateTime.now());
        Utilisateur saisieBy = etablissementService.getCurrentUtilisateur();
        if (saisieBy != null) note.setSaisieParId(saisieBy.getId());
        noteRepository.save(note);
        journalService.log("NOTE_SAISIE", "NOTES",
            eleve.getNom() + " " + eleve.getPrenom() + " — " + matiere.getNom() + " : " + valeur + "/20 (T" + trimestre + ")");
        return "redirect:/notes";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimerNote(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        noteRepository.findById(id)
            .filter(n -> n.getEleve() != null && etabId != null && etabId.equals(n.getEleve().getEtablissementId()))
            .ifPresent(n -> {
                journalService.log("NOTE_SUPPRIMÉE", "NOTES",
                    n.getEleve().getNom() + " " + n.getEleve().getPrenom() + " — "
                    + (n.getMatiere() != null ? n.getMatiere().getNom() : "?") + " T" + n.getTrimestre());
                noteRepository.delete(n);
            });
        return "redirect:/notes";
    }

    // ──────────────────────────────────────────────────────────────
    // Saisie rapide (filtrée pour les enseignants)
    // ──────────────────────────────────────────────────────────────

    @GetMapping("/saisie")
    public String saisieForm(
            @RequestParam(required = false) Long matiereId,
            @RequestParam(required = false) Long classeId,
            @RequestParam(required = false) Integer trimestre,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String titre,
            @RequestParam(required = false) Double coefficient,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEvaluation,
            Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());

        if (isEnseignant()) {
            // Filtrer matieres et classes selon autorisations
            List<EnseignantAutorisation> auths = getMyAutorisations();
            Set<Long> authMatiereIds = auths.stream().map(EnseignantAutorisation::getMatiereId).collect(Collectors.toSet());
            Set<Long> authClasseIds  = auths.stream().map(EnseignantAutorisation::getClasseId).collect(Collectors.toSet());

            List<Matiere> matieresFiltrees = matiereRepository.findByEtablissementIdOrderByNomAsc(etabId)
                .stream().filter(m -> authMatiereIds.contains(m.getId())).collect(Collectors.toList());
            List<Classe> classesFiltrees = classeRepository.findByEtablissementId(etabId)
                .stream().filter(c -> authClasseIds.contains(c.getId())).collect(Collectors.toList());

            model.addAttribute("matieres", matieresFiltrees);
            model.addAttribute("classes", classesFiltrees);

            // Mapping matière → classes autorisées pour filtrage JS dynamique
            Map<Long, List<Long>> matiereToClasses = new HashMap<>();
            for (EnseignantAutorisation a : auths) {
                matiereToClasses.computeIfAbsent(a.getMatiereId(), k -> new ArrayList<>()).add(a.getClasseId());
            }
            model.addAttribute("matiereToClasses", matiereToClasses);
            model.addAttribute("isEnseignant", true);
        } else {
            model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
            model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
            model.addAttribute("isEnseignant", false);
        }

        if (matiereId != null && classeId != null && trimestre != null) {
            populerContexteSaisie(model, matiereId, classeId, trimestre, type, titre, coefficient, dateEvaluation);
        }
        return isEnseignant() ? "notes-saisie-enseignant" : "notes-saisie";
    }

    /** Charge la liste des élèves de la classe + prefill des notes existantes + stats réelles. */
    private void populerContexteSaisie(Model model, Long matiereId, Long classeId, Integer trimestre,
                                        String type, String titre, Double coefficient, LocalDate dateEvaluation) {
        if (isEnseignant() && !isAutorise(matiereId, classeId)) {
            model.addAttribute("erreurAuth", "Accès refusé pour cette matière ou classe.");
            return;
        }
        List<Eleve> eleves = eleveRepository.findByClasseIdOrderByNomAsc(classeId);
        Matiere matiere = matiereRepository.findById(matiereId).orElse(null);
        Classe classe = classeRepository.findById(classeId).orElse(null);
        model.addAttribute("eleves", eleves);
        model.addAttribute("matiere", matiere);
        model.addAttribute("classe", classe);
        model.addAttribute("selectedMatiereId", matiereId);
        model.addAttribute("selectedClasseId", classeId);
        model.addAttribute("selectedTrimestre", trimestre);
        model.addAttribute("selectedType", type);
        model.addAttribute("selectedCoefficient", coefficient);
        model.addAttribute("selectedDate", dateEvaluation != null ? dateEvaluation : LocalDate.now());

        // Note existante pour cette evaluation precise (meme matiere + date + type) : prefill + stats reelles
        LocalDate dateEffective = dateEvaluation != null ? dateEvaluation : LocalDate.now();
        Map<Long, Note> noteParEleve = new HashMap<>();
        for (Eleve e : eleves) {
            noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(e, trimestre).stream()
                .filter(n -> n.getMatiere() != null && n.getMatiere().getId().equals(matiereId)
                          && Objects.equals(n.getDateEvaluation(), dateEffective)
                          && Objects.equals(n.getType(), type))
                .findFirst()
                .ifPresent(n -> noteParEleve.put(e.getId(), n));
        }
        model.addAttribute("noteParEleve", noteParEleve);

        String titreEffectif = titre;
        if (titreEffectif == null || titreEffectif.isBlank()) {
            titreEffectif = noteParEleve.values().stream()
                .map(Note::getTitre).filter(t -> t != null && !t.isBlank()).findFirst().orElse(null);
        }
        model.addAttribute("selectedTitre", titreEffectif);

        DoubleSummaryStatistics stats = noteParEleve.values().stream()
            .filter(n -> n.getValeur() != null)
            .mapToDouble(Note::getValeur)
            .summaryStatistics();
        model.addAttribute("moyenneClasse", stats.getCount() > 0 ? Math.round(stats.getAverage() * 100.0) / 100.0 : null);
        model.addAttribute("maxNote", stats.getCount() > 0 ? stats.getMax() : null);
        model.addAttribute("minNote", stats.getCount() > 0 ? stats.getMin() : null);
        model.addAttribute("nbNotesSaisies", stats.getCount());
    }

    @PostMapping("/saisie/batch")
    public String saveBatch(
            @RequestParam Long matiereId,
            @RequestParam Long classeId,
            @RequestParam Integer trimestre,
            @RequestParam String type,
            @RequestParam(required = false) String titre,
            @RequestParam Double coefficient,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEvaluation,
            @RequestParam(defaultValue = "PUBLIE") String statut,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes ra) {

        // Sécurité : vérifier l'autorisation de l'enseignant
        if (isEnseignant() && !isAutorise(matiereId, classeId)) {
            ra.addFlashAttribute("erreurAuth", "Accès refusé : vous n'êtes pas autorisé pour cette matière ou classe.");
            return "redirect:/notes/saisie";
        }

        Matiere matiere = matiereRepository.findById(matiereId).orElseThrow();
        Utilisateur saisieBy = etablissementService.getCurrentUtilisateur();
        LocalDateTime now = LocalDateTime.now();
        int saved = 0;

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            if (!key.startsWith("note_") || val == null || val.isBlank()) continue;
            try {
                Long eleveId = Long.parseLong(key.substring(5));
                double valeur = Double.parseDouble(val.replace(",", "."));
                if (valeur < 0 || valeur > 20) continue;
                Eleve eleve = eleveRepository.findById(eleveId).orElse(null);
                if (eleve == null) continue;

                // Upsert : reutilise la note existante pour cette meme evaluation (eleve+matiere+date+type)
                // plutot que d'en creer une nouvelle, pour permettre la transition brouillon -> publie.
                Long fEleveId = eleveId;
                Note note = noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, trimestre).stream()
                    .filter(n -> n.getMatiere() != null && n.getMatiere().getId().equals(matiereId)
                              && Objects.equals(n.getDateEvaluation(), dateEvaluation)
                              && Objects.equals(n.getType(), type))
                    .findFirst()
                    .orElseGet(Note::new);

                note.setEleve(eleve); note.setMatiere(matiere);
                note.setValeur(valeur); note.setCoefficient(coefficient);
                note.setType(type); note.setTitre(titre); note.setTrimestre(trimestre);
                note.setDateEvaluation(dateEvaluation);
                note.setCommentaire(allParams.get("commentaire_" + fEleveId));
                note.setStatut(statut);
                note.setSaisieAt(now);
                if (saisieBy != null) note.setSaisieParId(saisieBy.getId());
                noteRepository.save(note);
                saved++;
            } catch (NumberFormatException ignored) {}
        }
        String titreEncode = java.net.URLEncoder.encode(titre != null ? titre : "", java.nio.charset.StandardCharsets.UTF_8);
        return "redirect:/notes/saisie?matiereId=" + matiereId
             + "&classeId=" + classeId + "&trimestre=" + trimestre
             + "&type=" + type + "&titre=" + titreEncode + "&coefficient=" + coefficient
             + "&dateEvaluation=" + dateEvaluation + "&saved=" + saved;
    }

    // ──────────────────────────────────────────────────────────────
    // Import Excel des notes
    // ──────────────────────────────────────────────────────────────

    private static final String SESSION_IMPORT_KEY = "importNotesValides";

    public static class LigneImport implements Serializable {
        public Long eleveId;
        public String nomComplet;
        public String matricule;
        public Double valeur;
        public boolean valide;
        public String statut; // VALIDE, ELEVE_INCONNU, NOTE_INVALIDE
    }

    @PostMapping("/saisie/import")
    public String importerApercu(
            @RequestParam MultipartFile fichier,
            @RequestParam Long matiereId,
            @RequestParam Long classeId,
            @RequestParam Integer trimestre,
            @RequestParam String type,
            @RequestParam(required = false) String titre,
            @RequestParam Double coefficient,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEvaluation,
            HttpSession session,
            Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        String vue = isEnseignant() ? "notes-saisie-enseignant" : "notes-saisie";

        if (isEnseignant() && !isAutorise(matiereId, classeId)) {
            model.addAttribute("erreurAuth", "Accès refusé pour cette matière ou classe.");
            return vue;
        }

        List<Eleve> elevesClasse = eleveRepository.findByClasseIdOrderByNomAsc(classeId);
        Map<String, Eleve> parMatricule = new HashMap<>();
        for (Eleve e : elevesClasse) {
            if (e.getMatricule() != null) parMatricule.put(e.getMatricule().trim().toUpperCase(), e);
        }

        List<LigneImport> apercu = new ArrayList<>();
        if (fichier == null || fichier.isEmpty()) {
            model.addAttribute("erreurAuth", "Aucun fichier selectionne.");
        } else {
            try (Workbook wb = WorkbookFactory.create(fichier.getInputStream())) {
                Sheet sheet = wb.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // en-tête
                    String matricule = lireCelluleTexte(row.getCell(0));
                    String nom = lireCelluleTexte(row.getCell(1));
                    Double valeur = lireCelluleNombre(row.getCell(2));
                    if ((matricule == null || matricule.isBlank()) && valeur == null) continue; // ligne vide

                    LigneImport ligne = new LigneImport();
                    ligne.matricule = matricule != null ? matricule : "ID INVALIDE";
                    Eleve eleve = matricule != null ? parMatricule.get(matricule.trim().toUpperCase()) : null;
                    ligne.nomComplet = eleve != null ? (eleve.getNom() + " " + eleve.getPrenom()) : (nom != null ? nom : "?");
                    ligne.valeur = valeur;

                    if (eleve == null) {
                        ligne.valide = false;
                        ligne.statut = "ELEVE_INCONNU";
                    } else if (valeur == null || valeur < 0 || valeur > 20) {
                        ligne.valide = false;
                        ligne.statut = "NOTE_INVALIDE";
                        ligne.eleveId = eleve.getId();
                    } else {
                        ligne.valide = true;
                        ligne.statut = "VALIDE";
                        ligne.eleveId = eleve.getId();
                    }
                    apercu.add(ligne);
                }
            } catch (IOException | RuntimeException ex) {
                model.addAttribute("erreurAuth", "Fichier illisible. Utilisez un fichier Excel (.xlsx) valide.");
            }
        }

        List<LigneImport> validesUniquement = apercu.stream().filter(l -> l.valide).collect(Collectors.toList());
        session.setAttribute(SESSION_IMPORT_KEY, validesUniquement);

        model.addAttribute("importFichierNom", fichier != null ? fichier.getOriginalFilename() : null);
        model.addAttribute("importApercu", apercu);
        model.addAttribute("importTotal", apercu.size());
        model.addAttribute("importPret", validesUniquement.size());
        model.addAttribute("importErreurs", apercu.size() - validesUniquement.size());

        populerContexteSaisie(model, matiereId, classeId, trimestre, type, titre, coefficient, dateEvaluation);
        return vue;
    }

    @PostMapping("/saisie/import/confirmer")
    public String confirmerImport(
            @RequestParam Long matiereId,
            @RequestParam Long classeId,
            @RequestParam Integer trimestre,
            @RequestParam String type,
            @RequestParam Double coefficient,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEvaluation,
            HttpSession session,
            RedirectAttributes ra) {

        if (isEnseignant() && !isAutorise(matiereId, classeId)) {
            ra.addFlashAttribute("erreurAuth", "Accès refusé pour cette matière ou classe.");
            return "redirect:/notes/saisie";
        }

        @SuppressWarnings("unchecked")
        List<LigneImport> lignes = (List<LigneImport>) session.getAttribute(SESSION_IMPORT_KEY);
        int saved = 0;
        if (lignes != null) {
            Matiere matiere = matiereRepository.findById(matiereId).orElseThrow();
            Utilisateur saisieBy = etablissementService.getCurrentUtilisateur();
            LocalDateTime now = LocalDateTime.now();
            for (LigneImport l : lignes) {
                Eleve eleve = eleveRepository.findById(l.eleveId).orElse(null);
                if (eleve == null) continue;
                Note note = new Note();
                note.setEleve(eleve); note.setMatiere(matiere);
                note.setValeur(l.valeur); note.setCoefficient(coefficient);
                note.setType(type); note.setTrimestre(trimestre);
                note.setDateEvaluation(dateEvaluation);
                note.setSaisieAt(now);
                if (saisieBy != null) note.setSaisieParId(saisieBy.getId());
                noteRepository.save(note);
                saved++;
            }
            journalService.log("NOTES_IMPORTEES", "NOTES", saved + " note(s) importee(s) depuis Excel — " + matiere.getNom());
            session.removeAttribute(SESSION_IMPORT_KEY);
        }

        return "redirect:/notes/saisie?matiereId=" + matiereId
             + "&classeId=" + classeId + "&trimestre=" + trimestre
             + "&type=" + type + "&coefficient=" + coefficient
             + "&dateEvaluation=" + dateEvaluation + "&saved=" + saved;
    }

    private String lireCelluleTexte(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    private Double lireCelluleNombre(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
            if (cell.getCellType() == CellType.STRING) return Double.parseDouble(cell.getStringCellValue().trim().replace(",", "."));
        } catch (Exception ignored) {}
        return null;
    }

    @GetMapping("/bulletin/{eleveId}")
    public String bulletin(@PathVariable Long eleveId,
                           @RequestParam(defaultValue = "1") Integer trimestre,
                           Model model) {
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        List<Note> notes = noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, trimestre).stream()
            .filter(n -> !"BROUILLON".equals(n.getStatut()))
            .collect(Collectors.toList());
        double moyenneNum = notes.stream()
            .filter(n -> n.getValeur() != null && n.getCoefficient() != null)
            .mapToDouble(n -> n.getValeur() * n.getCoefficient()).sum();
        double totalCoef = notes.stream()
            .filter(n -> n.getCoefficient() != null)
            .mapToDouble(Note::getCoefficient).sum();
        double moyenneGenerale = totalCoef > 0 ? moyenneNum / totalCoef : 0;

        model.addAttribute("eleve", eleve);
        model.addAttribute("notes", notes);
        model.addAttribute("trimestre", trimestre);
        model.addAttribute("moyenneGenerale", Math.round(moyenneGenerale * 100.0) / 100.0);
        return "bulletin";
    }
}
