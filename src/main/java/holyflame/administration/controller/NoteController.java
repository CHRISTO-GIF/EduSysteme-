package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private EleveRepository eleveRepository;
    @Autowired
    private MatiereRepository matiereRepository;
    @Autowired
    private ClasseRepository classeRepository;
    @Autowired
    private EnseignantAutorisationRepository autorisationRepository;
    @Autowired
    private EtablissementService etablissementService;
    @Autowired
    private holyflame.administration.service.AnneeScolaireService anneeScolaireService;
    @Autowired
    private holyflame.administration.service.EvaluationQuotaService evaluationQuotaService;
    @Autowired
    private holyflame.administration.service.EmailService emailService;
    @Autowired
    private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired
    private JournalService journalService;

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
        if (u == null)
            return List.of();
        Long etabId = etablissementService.getCurrentEtablissementId();
        return autorisationRepository.findByEnseignantIdAndEtablissementId(u.getId(), etabId);
    }

    /** Vérifie que le teacher a le droit de saisir pour cette matière + classe */
    private boolean isAutorise(Long matiereId, Long classeId) {
        if (!isEnseignant())
            return true; // ADMIN ou SECRETAIRE : accès total
        return getMyAutorisations().stream()
                .anyMatch(a -> a.getMatiereId().equals(matiereId) && a.getClasseId().equals(classeId));
    }

    /**
     * Vérifie que la matière ET la classe appartiennent bien à l'établissement de
     * l'utilisateur connecté. isAutorise() ne protège que contre un ENSEIGNANT qui
     * saisirait hors de ses matières/classes autorisées ; pour ADMIN/SECRETAIRE elle
     * ne fait rien ("accès total"). Sans ce contrôle, un ADMIN/SECRETAIRE d'un
     * établissement pourrait fournir un matiereId/classeId d'un AUTRE établissement
     * (les identifiants sont des entiers séquentiels partagés entre tous les
     * établissements de l'application) et ainsi lire, modifier ou notifier par email
     * des notes/élèves qui ne lui appartiennent pas.
     */
    private void verifierAppartenanceEtablissement(Matiere matiere, Classe classe) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null || matiere == null || classe == null
                || !etabId.equals(matiere.getEtablissementId())
                || !etabId.equals(classe.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Matière ou classe introuvable dans cet établissement.");
        }
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
        model.addAttribute("eleves", eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId));
        model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
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
            @RequestParam(required = false, defaultValue = "false") boolean confirmerQuota,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        Matiere matiere = matiereRepository.findById(matiereId).orElseThrow();
        if (etabId == null || !etabId.equals(eleve.getEtablissementId())
                || !etabId.equals(matiere.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Élève ou matière introuvable dans cet établissement.");
        }
        // La classe (et donc l'annee scolaire qui en decoule) est obligatoire : sans elle,
        // la note ne peut pas etre rattachee a un bulletin ni verrouillee a la cloture d'annee.
        if (eleve.getClasse() == null) {
            ra.addFlashAttribute("erreurAuth",
                    "Impossible d'enregistrer une note : " + eleve.getNom() + " " + eleve.getPrenom()
                            + " n'est affecté à aucune classe.");
            return "redirect:/notes";
        }
        String anneeScolaireNote = eleve.getClasse().getAnneeScolaire();
        if (anneeScolaireNote == null || anneeScolaireNote.isBlank()) {
            ra.addFlashAttribute("erreurAuth",
                    "Impossible d'enregistrer une note : la classe de " + eleve.getNom() + " " + eleve.getPrenom()
                            + " n'a pas d'année scolaire définie.");
            return "redirect:/notes";
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

        anneeScolaireService.verifierModifiable(anneeScolaireNote, etabId);

        LocalDate dateEffective = dateEvaluation != null ? dateEvaluation : horlogeService.aujourdHui();
        String typeNormalise = Note.normalizeType(type);

        if (!confirmerQuota) {
            String avertissement = evaluationQuotaService.verifierQuota(matiereId, eleve.getClasse().getId(),
                    trimestre, anneeScolaireNote, type, dateEffective, etabId);
            if (avertissement != null) {
                ra.addFlashAttribute("erreurAuth", avertissement
                        + " Cochez la confirmation dans le formulaire pour enregistrer quand même.");
                return "redirect:/notes";
            }
        }

        // Upsert : reutilise la note existante pour cette meme evaluation (eleve + matiere +
        // trimestre + date + type) au lieu d'en creer une nouvelle a chaque soumission.
        Note note = noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, trimestre).stream()
                .filter(n -> n.getMatiere() != null && n.getMatiere().getId().equals(matiereId)
                        && Objects.equals(n.getDateEvaluation(), dateEffective)
                        && Objects.equals(Note.normalizeType(n.getType()), typeNormalise))
                .findFirst()
                .orElseGet(Note::new);

        note.setEleve(eleve);
        note.setMatiere(matiere);
        note.setValeur(valeur);
        note.setCoefficient(coefficient != null ? coefficient : matiere.getCoefficient());
        note.setType(type);
        note.setTrimestre(trimestre);
        note.setAnneeScolaire(anneeScolaireNote);
        note.setDateEvaluation(dateEffective);
        note.setCommentaire(commentaire);
        note.setSaisieAt(horlogeService.maintenant());
        Utilisateur saisieBy = etablissementService.getCurrentUtilisateur();
        if (saisieBy != null)
            note.setSaisieParId(saisieBy.getId());
        noteRepository.save(note);
        journalService.log("NOTE_SAISIE", "NOTES",
                eleve.getNom() + " " + eleve.getPrenom() + " — " + matiere.getNom() + " : " + valeur + "/20 (T"
                        + trimestre + ")");
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
                                    + (n.getMatiere() != null ? n.getMatiere().getNom() : "?") + " T"
                                    + n.getTrimestre());
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
            Set<Long> authMatiereIds = auths.stream().map(EnseignantAutorisation::getMatiereId)
                    .collect(Collectors.toSet());
            Set<Long> authClasseIds = auths.stream().map(EnseignantAutorisation::getClasseId)
                    .collect(Collectors.toSet());

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

    /**
     * Charge la liste des élèves de la classe + prefill des notes existantes +
     * stats réelles.
     */
    private void populerContexteSaisie(Model model, Long matiereId, Long classeId, Integer trimestre,
            String type, String titre, Double coefficient, LocalDate dateEvaluation) {
        if (isEnseignant() && !isAutorise(matiereId, classeId)) {
            model.addAttribute("erreurAuth", "Accès refusé pour cette matière ou classe.");
            return;
        }
        Matiere matiere = matiereRepository.findById(matiereId).orElse(null);
        Classe classe = classeRepository.findById(classeId).orElse(null);
        verifierAppartenanceEtablissement(matiere, classe);
        List<Eleve> eleves = eleveRepository.findByClasseIdOrderByNomAsc(classeId);
        model.addAttribute("eleves", eleves);
        model.addAttribute("matiere", matiere);
        model.addAttribute("classe", classe);
        model.addAttribute("selectedMatiereId", matiereId);
        model.addAttribute("selectedClasseId", classeId);
        model.addAttribute("selectedTrimestre", trimestre);
        model.addAttribute("selectedType", type);
        // Le coefficient de l'evaluation est pre-rempli avec le coefficient reel de la
        // matiere plutot qu'une valeur arbitraire, pour que la moyenne generale reflete
        // vraiment le poids configure de chaque matiere.
        Double coefficientEffectif = coefficient != null ? coefficient
                : (matiere != null && matiere.getCoefficient() != null ? matiere.getCoefficient() : 1.0);
        model.addAttribute("selectedCoefficient", coefficientEffectif);
        model.addAttribute("selectedDate", dateEvaluation != null ? dateEvaluation : horlogeService.aujourdHui());

        // Note existante pour cette evaluation precise (meme matiere + date + type) :
        // prefill + stats reelles
        LocalDate dateEffective = dateEvaluation != null ? dateEvaluation : horlogeService.aujourdHui();
        String typeNormalise = Note.normalizeType(type);

        // Avertissement pedagogique (pas un blocage en base) : au-dela du nombre de devoirs
        // configure (2 ou 3 selon l'etablissement) ou d'un 2e examen pour cette matiere/trimestre,
        // on previent l'enseignant avant meme qu'il ne remplisse les notes.
        if (classe != null && classe.getAnneeScolaire() != null) {
            String avertissementQuota = evaluationQuotaService.verifierQuota(matiereId, classeId, trimestre,
                    classe.getAnneeScolaire(), type, dateEffective, etablissementService.getCurrentEtablissementId());
            model.addAttribute("avertissementQuota", avertissementQuota);
        }
        Map<Long, Note> noteParEleve = new HashMap<>();
        for (Eleve e : eleves) {
            noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(e, trimestre).stream()
                    .filter(n -> n.getMatiere() != null && n.getMatiere().getId().equals(matiereId)
                            && Objects.equals(n.getDateEvaluation(), dateEffective)
                            && Objects.equals(Note.normalizeType(n.getType()), typeNormalise))
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
        model.addAttribute("moyenneClasse",
                stats.getCount() > 0 ? Math.round(stats.getAverage() * 100.0) / 100.0 : null);
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
            @RequestParam(required = false, defaultValue = "1") Double coefficient,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEvaluation,
            @RequestParam(defaultValue = "PUBLIE") String statut,
            @RequestParam(required = false, defaultValue = "false") boolean confirmerQuota,
            @RequestParam Map<String, String> allParams,
            RedirectAttributes ra) {

        // Sécurité : vérifier l'autorisation de l'enseignant
        if (isEnseignant() && !isAutorise(matiereId, classeId)) {
            ra.addFlashAttribute("erreurAuth", "Accès refusé : vous n'êtes pas autorisé pour cette matière ou classe.");
            return "redirect:/notes/saisie";
        }

        Matiere matiere = matiereRepository.findById(matiereId).orElseThrow();
        Classe classe = classeRepository.findById(classeId).orElseThrow();
        verifierAppartenanceEtablissement(matiere, classe);
        Long etabId = etablissementService.getCurrentEtablissementId();
        anneeScolaireService.verifierModifiable(classe.getAnneeScolaire(), etabId);

        // Avertissement pedagogique (2/3 devoirs ou 1 examen max par matiere/trimestre) : non
        // confirme, on n'enregistre rien et on renvoie vers l'etape de saisie ou le meme
        // avertissement sera reaffiche avec la case a cocher pour confirmer.
        if (!confirmerQuota) {
            String avertissement = evaluationQuotaService.verifierQuota(matiereId, classeId, trimestre,
                    classe.getAnneeScolaire(), type, dateEvaluation, etabId);
            if (avertissement != null) {
                ra.addFlashAttribute("erreurAuth", avertissement + " Cochez la confirmation pour enregistrer quand même.");
                String titreEncodeRetour = java.net.URLEncoder.encode(titre != null ? titre : "",
                        java.nio.charset.StandardCharsets.UTF_8);
                return "redirect:/notes/saisie?matiereId=" + matiereId + "&classeId=" + classeId
                        + "&trimestre=" + trimestre + "&type=" + type + "&titre=" + titreEncodeRetour
                        + "&coefficient=" + coefficient + "&dateEvaluation=" + dateEvaluation;
            }
        }

        Utilisateur saisieBy = etablissementService.getCurrentUtilisateur();
        LocalDateTime now = horlogeService.maintenant();
        String typeNormalise = Note.normalizeType(type);
        int saved = 0;
        int ignoresSansClasseOuAnnee = 0;

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            if (!key.startsWith("note_") || val == null || val.isBlank())
                continue;
            try {
                Long eleveId = Long.parseLong(key.substring(5));
                double valeur = Double.parseDouble(val.replace(",", "."));
                if (valeur < 0 || valeur > 20)
                    continue;
                Eleve eleve = eleveRepository.findById(eleveId).orElse(null);
                if (eleve == null)
                    continue;

                // La classe (et donc l'annee scolaire) est obligatoire pour qu'une note soit
                // rattachable a un bulletin : on ignore silencieusement (et on le signale) tout
                // eleve qui n'en aurait pas plutot que d'enregistrer une note orpheline.
                String anneeScolaireNote = eleve.getClasse() != null ? eleve.getClasse().getAnneeScolaire() : null;
                if (anneeScolaireNote == null || anneeScolaireNote.isBlank()) {
                    ignoresSansClasseOuAnnee++;
                    continue;
                }

                // Upsert : reutilise la note existante pour cette meme evaluation
                // (eleve+matiere+date+type normalise) plutot que d'en creer une nouvelle, pour
                // permettre la transition brouillon -> publie sans doublonner la ligne.
                Long fEleveId = eleveId;
                Note note = noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, trimestre).stream()
                        .filter(n -> n.getMatiere() != null && n.getMatiere().getId().equals(matiereId)
                                && Objects.equals(n.getDateEvaluation(), dateEvaluation)
                                && Objects.equals(Note.normalizeType(n.getType()), typeNormalise))
                        .findFirst()
                        .orElseGet(Note::new);

                note.setEleve(eleve);
                note.setMatiere(matiere);
                note.setValeur(valeur);
                note.setCoefficient(coefficient);
                note.setType(type);
                note.setTitre(titre);
                note.setTrimestre(trimestre);
                note.setAnneeScolaire(anneeScolaireNote);
                note.setDateEvaluation(dateEvaluation);
                note.setCommentaire(allParams.get("commentaire_" + fEleveId));
                note.setStatut(statut);
                note.setSaisieAt(now);
                if (saisieBy != null)
                    note.setSaisieParId(saisieBy.getId());
                noteRepository.save(note);
                saved++;
            } catch (NumberFormatException ignored) {
            }
        }
        if (ignoresSansClasseOuAnnee > 0) {
            ra.addFlashAttribute("erreurAuth", ignoresSansClasseOuAnnee
                    + " note(s) non enregistrée(s) : élève(s) sans classe ou sans année scolaire définie.");
        }
        String titreEncode = java.net.URLEncoder.encode(titre != null ? titre : "",
                java.nio.charset.StandardCharsets.UTF_8);
        return "redirect:/notes/saisie?matiereId=" + matiereId
                + "&classeId=" + classeId + "&trimestre=" + trimestre
                + "&type=" + type + "&titre=" + titreEncode + "&coefficient=" + coefficient
                + "&dateEvaluation=" + dateEvaluation + "&saved=" + saved;
    }

    /**
     * Notifie par email les parents des eleves dont la note vient d'etre publiee pour cette
     * evaluation precise (meme matiere + trimestre + date + type). N'envoie qu'aux notes
     * PUBLIE (jamais aux brouillons) et ignore silencieusement les eleves sans email parent.
     */
    @PostMapping("/saisie/notifier")
    public String notifierParents(
            @RequestParam Long matiereId,
            @RequestParam Long classeId,
            @RequestParam Integer trimestre,
            @RequestParam String type,
            @RequestParam(required = false) String titre,
            @RequestParam(required = false, defaultValue = "1") Double coefficient,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEvaluation,
            RedirectAttributes ra) {

        if (isEnseignant() && !isAutorise(matiereId, classeId)) {
            ra.addFlashAttribute("erreurAuth", "Accès refusé pour cette matière ou classe.");
            return "redirect:/notes/saisie";
        }

        Matiere matiere = matiereRepository.findById(matiereId).orElseThrow();
        Classe classe = classeRepository.findById(classeId).orElseThrow();
        verifierAppartenanceEtablissement(matiere, classe);
        String typeNormalise = Note.normalizeType(type);
        String libelleType = libelleTypeEvaluation(typeNormalise);

        int envoyes = 0, sansEmail = 0;
        for (Eleve eleve : eleveRepository.findByClasseIdOrderByNomAsc(classeId)) {
            Note note = noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, trimestre).stream()
                    .filter(n -> n.getMatiere() != null && n.getMatiere().getId().equals(matiereId)
                            && Objects.equals(n.getDateEvaluation(), dateEvaluation)
                            && Objects.equals(Note.normalizeType(n.getType()), typeNormalise)
                            && "PUBLIE".equals(n.getStatut()) && n.getValeur() != null)
                    .findFirst().orElse(null);
            if (note == null)
                continue;

            String destinataire = adresseParentEleve(eleve);
            if (destinataire == null || destinataire.isBlank()) {
                sansEmail++;
                continue;
            }
            String sujet = "Nouvelle note publiée — " + matiere.getNom() + " (" + classe.getNom() + ")";
            String corps = "<p>Bonjour,</p>"
                    + "<p>Une nouvelle note vient d'être publiée pour <strong>" + eleve.getPrenom() + " " + eleve.getNom()
                    + "</strong>.</p>"
                    + "<table style=\"border-collapse:collapse;margin:16px 0;\">"
                    + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Matière</td><td><strong>" + matiere.getNom()
                    + "</strong></td></tr>"
                    + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Évaluation</td><td>"
                    + (titre != null && !titre.isBlank() ? titre : libelleType) + "</td></tr>"
                    + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Trimestre</td><td>" + trimestre + "</td></tr>"
                    + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Note</td><td><strong>" + note.getValeur()
                    + " / 20</strong></td></tr>"
                    + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Date</td><td>" + dateEvaluation + "</td></tr>"
                    + "</table>"
                    + "<p>Connectez-vous au portail parent pour consulter le bulletin complet.</p>";
            if (emailService.envoyer(destinataire, sujet, corps))
                envoyes++;
        }

        if (envoyes > 0) {
            ra.addFlashAttribute("successMsg", envoyes + " parent(s) notifié(s) par email."
                    + (sansEmail > 0 ? " " + sansEmail + " élève(s) sans email parent enregistré." : ""));
        } else {
            ra.addFlashAttribute("erreurAuth", "Aucune notification envoyée : aucune note publiée pour cette évaluation, "
                    + "ou aucun email parent enregistré, ou le service d'envoi n'est pas configuré.");
        }

        String titreEncode = java.net.URLEncoder.encode(titre != null ? titre : "",
                java.nio.charset.StandardCharsets.UTF_8);
        return "redirect:/notes/saisie?matiereId=" + matiereId
                + "&classeId=" + classeId + "&trimestre=" + trimestre
                + "&type=" + type + "&titre=" + titreEncode + "&coefficient=" + coefficient
                + "&dateEvaluation=" + dateEvaluation;
    }

    private String adresseParentEleve(Eleve eleve) {
        if (eleve.getPereEmail() != null && !eleve.getPereEmail().isBlank())
            return eleve.getPereEmail();
        if (eleve.getMereEmail() != null && !eleve.getMereEmail().isBlank())
            return eleve.getMereEmail();
        return eleve.getEmailParent();
    }

    private String libelleTypeEvaluation(String typeNormalise) {
        if (Note.TYPE_DEVOIR.equals(typeNormalise))
            return "Devoir 1";
        if (Note.TYPE_DEVOIR2.equals(typeNormalise))
            return "Devoir 2";
        if (Note.TYPE_DEVOIR3.equals(typeNormalise))
            return "Devoir 3";
        if (Note.TYPE_EXAMEN.equals(typeNormalise))
            return "Examen";
        if (Note.TYPE_PARTICIPATION.equals(typeNormalise))
            return "Participation";
        if (Note.TYPE_TP.equals(typeNormalise))
            return "Travaux pratiques";
        return typeNormalise;
    }

    // ──────────────────────────────────────────────────────────────
    // Import Excel des notes
    // ──────────────────────────────────────────────────────────────

    private static final String SESSION_IMPORT_KEY = "importNotesValides";

    /**
     * Modele Excel pre-rempli avec les eleves de la classe (Matricule + Nom), pret
     * a completer avec les notes.
     */
    @GetMapping("/saisie/import/modele")
    public void telechargerModeleNotes(@RequestParam Long classeId, HttpServletResponse response) throws IOException {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(classeId).orElse(null);
        if (classe == null || etabId == null || !etabId.equals(classe.getEtablissementId())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        List<Eleve> eleves = eleveRepository.findByClasseIdOrderByNomAsc(classeId);

        XSSFWorkbook wb = new XSSFWorkbook();
        var sheet = wb.createSheet("Notes");

        XSSFCellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFillForegroundColor(new XSSFColor(new byte[] { (byte) 0, (byte) 35, (byte) 111 }, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont headerFont = wb.createFont();
        headerFont.setColor(new XSSFColor(new byte[] { (byte) 255, (byte) 255, (byte) 255 }, null));
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        String[] entetes = { "Matricule", "Nom", "Note (sur 20)" };
        Row ligneEntete = sheet.createRow(0);
        for (int i = 0; i < entetes.length; i++) {
            Cell c = ligneEntete.createCell(i);
            c.setCellValue(entetes[i]);
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 24 * 256);
        }

        int rowNum = 1;
        for (Eleve e : eleves) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(e.getMatricule() != null ? e.getMatricule() : "");
            row.createCell(1).setCellValue(e.getNom() + " " + e.getPrenom());
            row.createCell(2); // note a completer
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"modele-import-notes-" + classe.getNom().replaceAll("\\s+", "-") + ".xlsx\"");
        wb.write(response.getOutputStream());
        wb.close();
    }

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
            @RequestParam(required = false, defaultValue = "1") Double coefficient,
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
        verifierAppartenanceEtablissement(
                matiereRepository.findById(matiereId).orElse(null),
                classeRepository.findById(classeId).orElse(null));

        List<Eleve> elevesClasse = eleveRepository.findByClasseIdOrderByNomAsc(classeId);
        Map<String, Eleve> parMatricule = new HashMap<>();
        for (Eleve e : elevesClasse) {
            if (e.getMatricule() != null)
                parMatricule.put(e.getMatricule().trim().toUpperCase(), e);
        }

        List<LigneImport> apercu = new ArrayList<>();
        if (fichier == null || fichier.isEmpty()) {
            model.addAttribute("erreurAuth", "Aucun fichier selectionne.");
        } else {
            try (Workbook wb = WorkbookFactory.create(fichier.getInputStream())) {
                Sheet sheet = wb.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0)
                        continue; // en-tête
                    String matricule = lireCelluleTexte(row.getCell(0));
                    String nom = lireCelluleTexte(row.getCell(1));
                    Double valeur = lireCelluleNombre(row.getCell(2));
                    if ((matricule == null || matricule.isBlank()) && valeur == null)
                        continue; // ligne vide

                    LigneImport ligne = new LigneImport();
                    ligne.matricule = matricule != null ? matricule : "ID INVALIDE";
                    Eleve eleve = matricule != null ? parMatricule.get(matricule.trim().toUpperCase()) : null;
                    ligne.nomComplet = eleve != null ? (eleve.getNom() + " " + eleve.getPrenom())
                            : (nom != null ? nom : "?");
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

        double moyenne = validesUniquement.stream()
                .mapToDouble(l -> l.valeur).average().orElse(0.0);

        model.addAttribute("importFichierNom", fichier != null ? fichier.getOriginalFilename() : null);
        model.addAttribute("importApercu", apercu);
        model.addAttribute("importTotal", apercu.size());
        model.addAttribute("importPret", validesUniquement.size());
        model.addAttribute("importErreurs", apercu.size() - validesUniquement.size());
        model.addAttribute("importMoyenne", Math.round(moyenne * 100.0) / 100.0);

        populerContexteSaisie(model, matiereId, classeId, trimestre, type, titre, coefficient, dateEvaluation);
        return vue;
    }

    @PostMapping("/saisie/import/confirmer")
    public String confirmerImport(
            @RequestParam Long matiereId,
            @RequestParam Long classeId,
            @RequestParam Integer trimestre,
            @RequestParam String type,
            @RequestParam(required = false, defaultValue = "1") Double coefficient,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEvaluation,
            @RequestParam(required = false, defaultValue = "false") boolean confirmerQuota,
            HttpSession session,
            RedirectAttributes ra) {

        if (isEnseignant() && !isAutorise(matiereId, classeId)) {
            ra.addFlashAttribute("erreurAuth", "Accès refusé pour cette matière ou classe.");
            return "redirect:/notes/saisie";
        }

        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classeImport = classeRepository.findById(classeId).orElseThrow();
        Matiere matiereImport = matiereRepository.findById(matiereId).orElseThrow();
        verifierAppartenanceEtablissement(matiereImport, classeImport);
        anneeScolaireService.verifierModifiable(classeImport.getAnneeScolaire(), etabId);

        if (!confirmerQuota) {
            String avertissement = evaluationQuotaService.verifierQuota(matiereId, classeId, trimestre,
                    classeImport.getAnneeScolaire(), type, dateEvaluation, etabId);
            if (avertissement != null) {
                ra.addFlashAttribute("erreurAuth", avertissement + " Cochez la confirmation pour importer quand même.");
                return "redirect:/notes/saisie?matiereId=" + matiereId + "&classeId=" + classeId
                        + "&trimestre=" + trimestre + "&type=" + type + "&coefficient=" + coefficient
                        + "&dateEvaluation=" + dateEvaluation;
            }
        }

        @SuppressWarnings("unchecked")
        List<LigneImport> lignes = (List<LigneImport>) session.getAttribute(SESSION_IMPORT_KEY);
        int saved = 0;
        int ignoresSansClasseOuAnnee = 0;
        if (lignes != null) {
            Matiere matiere = matiereImport;
            Utilisateur saisieBy = etablissementService.getCurrentUtilisateur();
            LocalDateTime now = horlogeService.maintenant();
            String typeNormalise = Note.normalizeType(type);
            for (LigneImport l : lignes) {
                Eleve eleve = eleveRepository.findById(l.eleveId).orElse(null);
                if (eleve == null)
                    continue;
                String anneeScolaireNote = eleve.getClasse() != null ? eleve.getClasse().getAnneeScolaire() : null;
                if (anneeScolaireNote == null || anneeScolaireNote.isBlank()) {
                    ignoresSansClasseOuAnnee++;
                    continue;
                }

                // Upsert : evite les doublons si le meme fichier est reimporte pour la meme
                // evaluation (eleve+matiere+trimestre+date+type normalise).
                Note note = noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, trimestre).stream()
                        .filter(n -> n.getMatiere() != null && n.getMatiere().getId().equals(matiereId)
                                && Objects.equals(n.getDateEvaluation(), dateEvaluation)
                                && Objects.equals(Note.normalizeType(n.getType()), typeNormalise))
                        .findFirst()
                        .orElseGet(Note::new);
                note.setEleve(eleve);
                note.setMatiere(matiere);
                note.setValeur(l.valeur);
                note.setCoefficient(coefficient);
                note.setType(type);
                note.setTrimestre(trimestre);
                note.setAnneeScolaire(anneeScolaireNote);
                note.setDateEvaluation(dateEvaluation);
                note.setSaisieAt(now);
                if (saisieBy != null)
                    note.setSaisieParId(saisieBy.getId());
                noteRepository.save(note);
                saved++;
            }
            journalService.log("NOTES_IMPORTEES", "NOTES",
                    saved + " note(s) importee(s) depuis Excel — " + matiere.getNom());
            session.removeAttribute(SESSION_IMPORT_KEY);
        }
        if (ignoresSansClasseOuAnnee > 0) {
            ra.addFlashAttribute("erreurAuth", ignoresSansClasseOuAnnee
                    + " note(s) non importée(s) : élève(s) sans classe ou sans année scolaire définie.");
        }

        return "redirect:/notes/saisie?matiereId=" + matiereId
                + "&classeId=" + classeId + "&trimestre=" + trimestre
                + "&type=" + type + "&coefficient=" + coefficient
                + "&dateEvaluation=" + dateEvaluation + "&saved=" + saved;
    }

    private String lireCelluleTexte(Cell cell) {
        if (cell == null)
            return null;
        if (cell.getCellType() == CellType.STRING)
            return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC)
            return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    private Double lireCelluleNombre(Cell cell) {
        if (cell == null)
            return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC)
                return cell.getNumericCellValue();
            if (cell.getCellType() == CellType.STRING)
                return Double.parseDouble(cell.getStringCellValue().trim().replace(",", "."));
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Ancien point d'entree du bulletin, encore reference depuis le tableau
     * enseignant. Il alimentait la vue "bulletin" avec un modele incomplet (ni
     * poles, ni rang, ni en-tete etablissement) : le tableau des matieres —
     * donc les colonnes DEV 1 / DEV 2 / EXAMEN — restait vide. On redirige
     * desormais vers le bulletin canonique, calcule par BulletinService.
     */
    @GetMapping("/bulletin/{eleveId}")
    public String bulletin(@PathVariable Long eleveId,
            @RequestParam(defaultValue = "1") Integer trimestre) {
        return "redirect:/bulletins/" + eleveId + "?trimestre=" + trimestre;
    }
}
