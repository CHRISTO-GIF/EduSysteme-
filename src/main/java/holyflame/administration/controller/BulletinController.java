package holyflame.administration.controller;

import holyflame.administration.model.Absence;
import holyflame.administration.model.Conduite;
import holyflame.administration.model.DocumentEleve;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Note;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.AbsenceRepository;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.ConduiteRepository;
import holyflame.administration.repository.DocumentEleveRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/bulletins")
public class BulletinController {

    private static final List<String> ORDRE_POLES = List.of("SCIENTIFIQUE", "LITTERAIRE", "ARTS_SPORT", "AUTRE");

    @Autowired private EleveRepository eleveRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ConduiteRepository conduiteRepository;
    @Autowired private DocumentEleveRepository documentEleveRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private EtablissementService etablissementService;

    @GetMapping
    public String liste(@RequestParam(required = false) Long classeId, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Eleve> eleves = classeId != null
            ? eleveRepository.findByClasseIdOrderByNomAsc(classeId)
            : eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);

        model.addAttribute("eleves",  eleves);
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("classeId", classeId);
        return "bulletins";
    }

    // ── Impression en lot : tous les bulletins d'une classe pour un trimestre ──
    @GetMapping("/impression")
    public String impressionClasse(@RequestParam(required = false) Long classeId,
                                   @RequestParam(defaultValue = "1") Integer trimestre,
                                   Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();

        List<Eleve> eleves = classeId != null
            ? eleveRepository.findByClasseIdOrderByNomAsc(classeId)
            : eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);

        List<Map<String, Object>> bulletins = new ArrayList<>();
        for (Eleve eleve : eleves) {
            List<Note> notes = noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, trimestre).stream()
                .filter(n -> !"BROUILLON".equals(n.getStatut()))
                .toList();
            double somme = notes.stream()
                .filter(n -> n.getValeur() != null && n.getCoefficient() != null)
                .mapToDouble(n -> n.getValeur() * n.getCoefficient()).sum();
            double totalCoef = notes.stream()
                .filter(n -> n.getCoefficient() != null)
                .mapToDouble(Note::getCoefficient).sum();
            double moyenne = totalCoef > 0 ? Math.round((somme / totalCoef) * 100.0) / 100.0 : 0;

            Map<String, Object> b = new LinkedHashMap<>();
            b.put("eleve",      eleve);
            b.put("notes",      notes);
            b.put("moyenne",    moyenne);
            b.put("mention",    getMention(moyenne));
            b.put("mentionCss", getMentionCss(moyenne));
            b.put("appreciation", getAppreciation(moyenne));
            bulletins.add(b);
        }

        bulletins.sort((a, b) -> Double.compare((Double) b.get("moyenne"), (Double) a.get("moyenne")));
        for (int i = 0; i < bulletins.size(); i++) {
            bulletins.get(i).put("rang", i + 1);
            bulletins.get(i).put("effectif", bulletins.size());
        }

        String nomEtab = parametreRepository
            .findByCleAndEtablissementId("NOM_ETABLISSEMENT", etabId)
            .map(p -> p.getValeur()).orElse("HolyFlame");
        String classeNom = classeId != null
            ? classeRepository.findById(classeId).map(c -> c.getNom()).orElse("Toutes les classes")
            : "Toutes les classes";

        model.addAttribute("bulletins",  bulletins);
        model.addAttribute("trimestre",  trimestre);
        model.addAttribute("classeNom",  classeNom);
        model.addAttribute("nomEtab",    nomEtab);
        model.addAttribute("classeId",   classeId);
        model.addAttribute("classes",    classeRepository.findByEtablissementId(etabId));
        model.addAttribute("trimestres", List.of(1, 2, 3));
        return "bulletins-impression";
    }

    private String getMention(double moyenne) {
        if (moyenne >= 16) return "TRÈS BIEN";
        if (moyenne >= 14) return "BIEN";
        if (moyenne >= 12) return "ASSEZ BIEN";
        if (moyenne >= 10) return "PASSABLE";
        return "INSUFFISANT";
    }

    private String getMentionCss(double moyenne) {
        if (moyenne >= 16) return "bg-success";
        if (moyenne >= 14) return "bg-primary";
        if (moyenne >= 12) return "bg-info text-dark";
        if (moyenne >= 10) return "bg-warning text-dark";
        return "bg-danger";
    }

    private String getAppreciation(double moyenne) {
        if (moyenne >= 18) return "Résultats exceptionnels. Félicitations du conseil de classe.";
        if (moyenne >= 16) return "Excellents résultats. Toutes nos félicitations.";
        if (moyenne >= 14) return "Très bons résultats. Encouragements du conseil de classe.";
        if (moyenne >= 12) return "Bons résultats. Peut encore progresser.";
        if (moyenne >= 10) return "Résultats satisfaisants. Des efforts restent nécessaires.";
        if (moyenne >= 8)  return "Résultats insuffisants. Un travail sérieux s'impose.";
        return "Résultats très insuffisants. Une remise en question est nécessaire.";
    }

    private String libellePole(String pole) {
        if ("SCIENTIFIQUE".equals(pole)) return "Pôle Scientifique";
        if ("LITTERAIRE".equals(pole)) return "Pôle Littéraire & Langues";
        if ("ARTS_SPORT".equals(pole)) return "Pôle Arts & Sport";
        return "Autres Matières";
    }

    /** Moyenne ponderee d'un eleve pour une liste de notes deja filtrees (trimestre + statut publie). */
    private double moyennePonderee(List<Note> notes) {
        double somme = notes.stream().filter(n -> n.getValeur() != null && n.getCoefficient() != null)
            .mapToDouble(n -> n.getValeur() * n.getCoefficient()).sum();
        double coef = notes.stream().filter(n -> n.getCoefficient() != null)
            .mapToDouble(Note::getCoefficient).sum();
        return coef > 0 ? somme / coef : 0;
    }

    @GetMapping("/{eleveId}")
    public String bulletin(@PathVariable Long eleveId,
                           @RequestParam(defaultValue = "1") Integer trimestre,
                           Model model,
                           RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElse(null);
        if (eleve == null || (etabId != null && !etabId.equals(eleve.getEtablissementId()))) {
            ra.addFlashAttribute("erreur", "Élève introuvable.");
            return "redirect:/bulletins";
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean estParent = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_PARENT".equals(a.getAuthority()));
        if (estParent) {
            String email = auth.getName();
            boolean estMonEnfant = email.equalsIgnoreCase(eleve.getEmailParent())
                || email.equalsIgnoreCase(eleve.getPereEmail())
                || email.equalsIgnoreCase(eleve.getMereEmail());
            if (!estMonEnfant) {
                ra.addFlashAttribute("erreur", "Vous n'avez pas accès au bulletin de cet élève.");
                return "redirect:/portail-parent";
            }
        }

        List<Note> notes = noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, trimestre).stream()
            .filter(n -> !"BROUILLON".equals(n.getStatut()))
            .toList();
        double moyenne = moyennePonderee(notes);
        double moyenneArrondie = Math.round(moyenne * 100.0) / 100.0;

        // ── Rang, moyenne de classe et stats reelles par matiere (calculees sur tous les camarades) ──
        int rang = 1; int effectif = 1; double moyenneClasseGenerale = 0;
        Map<Long, List<Double>> moyennesParMatiereClasse = new LinkedHashMap<>();
        if (eleve.getClasse() != null) {
            List<Eleve> camarades = eleveRepository.findByClasseIdOrderByNomAsc(eleve.getClasse().getId());
            effectif = camarades.size();
            double sommeMoyennes = 0;
            for (Eleve cam : camarades) {
                List<Note> notesCam = noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(cam, trimestre).stream()
                    .filter(n -> !"BROUILLON".equals(n.getStatut())).toList();
                double moyenneCam = moyennePonderee(notesCam);
                sommeMoyennes += moyenneCam;
                if (!cam.getId().equals(eleve.getId()) && moyenneCam > moyenne) rang++;

                Map<Long, List<Note>> parMatiereCam = new LinkedHashMap<>();
                for (Note n : notesCam) {
                    if (n.getMatiere() == null || n.getValeur() == null) continue;
                    parMatiereCam.computeIfAbsent(n.getMatiere().getId(), k -> new ArrayList<>()).add(n);
                }
                for (Map.Entry<Long, List<Note>> e : parMatiereCam.entrySet()) {
                    double moyMatiereCam = moyennePonderee(e.getValue());
                    moyennesParMatiereClasse.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(moyMatiereCam);
                }
            }
            moyenneClasseGenerale = effectif > 0 ? sommeMoyennes / effectif : 0;
        }

        // ── Regroupement des notes de l'eleve par matiere, puis par pole ──
        Map<Long, List<Note>> notesParMatiere = new LinkedHashMap<>();
        for (Note n : notes) {
            if (n.getMatiere() == null) continue;
            notesParMatiere.computeIfAbsent(n.getMatiere().getId(), k -> new ArrayList<>()).add(n);
        }

        Map<String, List<Map<String, Object>>> poles = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Note>> entry : notesParMatiere.entrySet()) {
            List<Note> notesMatiere = entry.getValue();
            Matiere matiere = notesMatiere.get(0).getMatiere();
            double moyMatiere = moyennePonderee(notesMatiere);
            List<Double> moyClasseListe = moyennesParMatiereClasse.getOrDefault(entry.getKey(), List.of());
            double moyClasseMatiere = moyClasseListe.isEmpty() ? moyMatiere
                : moyClasseListe.stream().mapToDouble(Double::doubleValue).average().orElse(moyMatiere);
            double minClasse = moyClasseListe.isEmpty() ? moyMatiere : moyClasseListe.stream().mapToDouble(Double::doubleValue).min().orElse(moyMatiere);
            double maxClasse = moyClasseListe.isEmpty() ? moyMatiere : moyClasseListe.stream().mapToDouble(Double::doubleValue).max().orElse(moyMatiere);
            String appreciationMatiere = notesMatiere.stream()
                .filter(n -> n.getCommentaire() != null && !n.getCommentaire().isBlank())
                .reduce((first, second) -> second) // le plus recent (liste triee par date d'evaluation croissante en base)
                .map(Note::getCommentaire).orElse("");

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("matiere", matiere);
            ligne.put("moyenne", Math.round(moyMatiere * 100.0) / 100.0);
            ligne.put("moyenneClasse", Math.round(moyClasseMatiere * 100.0) / 100.0);
            ligne.put("min", Math.round(minClasse * 100.0) / 100.0);
            ligne.put("max", Math.round(maxClasse * 100.0) / 100.0);
            ligne.put("appreciation", appreciationMatiere);

            String pole = matiere.getPole() != null ? matiere.getPole() : "AUTRE";
            poles.computeIfAbsent(pole, k -> new ArrayList<>()).add(ligne);
        }
        // Reordonner selon l'ordre de poles souhaite
        Map<String, List<Map<String, Object>>> polesOrdonnes = new LinkedHashMap<>();
        for (String p : ORDRE_POLES) {
            if (poles.containsKey(p)) polesOrdonnes.put(libellePole(p), poles.get(p));
        }
        poles.forEach((k, v) -> { if (!ORDRE_POLES.contains(k)) polesOrdonnes.put(libellePole(k), v); });

        // ── Professeur titulaire ──
        Utilisateur professeurTitulaire = null;
        if (eleve.getClasse() != null && eleve.getClasse().getProfesseurTitulaireId() != null) {
            professeurTitulaire = utilisateurRepository.findById(eleve.getClasse().getProfesseurTitulaireId()).orElse(null);
        }

        // ── Chef d'etablissement (parametre reel) ──
        String chefEtablissement = parametreRepository.findByCleAndEtablissementId("CONTACT_PRINCIPAL", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse(null);
        String nomEtab = parametreRepository.findByCleAndEtablissementId("NOM_ETABLISSEMENT", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse("HolyFlame");
        String adresseEtab = parametreRepository.findByCleAndEtablissementId("ADRESSE_ETABLISSEMENT", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse("");
        String emailEtab = parametreRepository.findByCleAndEtablissementId("EMAIL_ECOLE", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse("");
        String logoPath = parametreRepository.findByCleAndEtablissementId("LOGO_ETAB", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse(null);

        // ── Photo reelle de l'eleve si deja uploadee lors de l'inscription ──
        String photoPath = documentEleveRepository.findByEleveIdOrderByDateUploadDesc(eleveId).stream()
            .filter(d -> "PHOTO_IDENTITE".equals(d.getTypeDocument()))
            .findFirst()
            .map(DocumentEleve::getCheminFichier)
            .orElse(null);

        // ── Conduite reelle (si saisie) ──
        String anneeScolaire = eleve.getClasse() != null ? eleve.getClasse().getAnneeScolaire() : null;
        Conduite conduite = anneeScolaire != null
            ? conduiteRepository.findByEleveAndTrimestreAndAnneeScolaire(eleve, trimestre, anneeScolaire).orElse(null)
            : null;

        // ── Assiduite reelle (comptage total, non filtre par trimestre car aucune date de trimestre n'est modelisee) ──
        List<Absence> absences = absenceRepository.findByEleveIdOrderByDateDesc(eleveId);
        long absencesJustifiees = absences.stream().filter(Absence::isEstJustifiee).count();
        long absencesNonJustifiees = absences.size() - absencesJustifiees;

        // ── Distinctions reelles calculees ──
        boolean felicitations = moyenneArrondie >= 16;
        boolean tableauHonneur = rang <= 3;

        String codeVerification = "ETAB" + etabId + "-EL" + eleveId + "-T" + trimestre + "-" + (anneeScolaire != null ? anneeScolaire.replace("-", "") : "");

        model.addAttribute("eleve", eleve);
        model.addAttribute("poles", polesOrdonnes);
        model.addAttribute("trimestre", trimestre);
        model.addAttribute("moyenneGenerale", moyenneArrondie);
        model.addAttribute("moyenneClasseGenerale", Math.round(moyenneClasseGenerale * 100.0) / 100.0);
        model.addAttribute("mention", getMention(moyenneArrondie));
        model.addAttribute("mentionCss", getMentionCss(moyenneArrondie));
        model.addAttribute("appreciation", getAppreciation(moyenneArrondie));
        model.addAttribute("rang", rang);
        model.addAttribute("effectif", effectif);
        model.addAttribute("professeurTitulaire", professeurTitulaire);
        model.addAttribute("chefEtablissement", chefEtablissement);
        model.addAttribute("nomEtab", nomEtab);
        model.addAttribute("adresseEtab", adresseEtab);
        model.addAttribute("emailEtab", emailEtab);
        model.addAttribute("logoPath", logoPath);
        model.addAttribute("photoPath", photoPath);
        model.addAttribute("conduite", conduite);
        model.addAttribute("absencesJustifiees", absencesJustifiees);
        model.addAttribute("absencesNonJustifiees", absencesNonJustifiees);
        model.addAttribute("felicitations", felicitations);
        model.addAttribute("tableauHonneur", tableauHonneur);
        model.addAttribute("codeVerification", codeVerification);
        model.addAttribute("anneeScolaire", anneeScolaire);
        model.addAttribute("estAdminOuEnseignant", auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_ENSEIGNANT".equals(a.getAuthority())));
        return "bulletin";
    }

    @PostMapping("/{eleveId}/conduite")
    public String enregistrerConduite(@PathVariable Long eleveId,
                                      @RequestParam Integer trimestre,
                                      @RequestParam String evaluation,
                                      @RequestParam(required = false) String commentaire,
                                      RedirectAttributes ra) {
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        String anneeScolaire = eleve.getClasse() != null ? eleve.getClasse().getAnneeScolaire() : null;
        if (anneeScolaire == null) {
            ra.addFlashAttribute("erreur", "Impossible d'enregistrer la conduite : aucune classe/annee scolaire associee.");
            return "redirect:/bulletins/" + eleveId + "?trimestre=" + trimestre;
        }
        Conduite conduite = conduiteRepository.findByEleveAndTrimestreAndAnneeScolaire(eleve, trimestre, anneeScolaire)
            .orElseGet(Conduite::new);
        conduite.setEleve(eleve);
        conduite.setTrimestre(trimestre);
        conduite.setAnneeScolaire(anneeScolaire);
        conduite.setEvaluation(evaluation);
        conduite.setCommentaire(commentaire);
        conduite.setSaisieAt(LocalDateTime.now());
        Utilisateur saisiPar = etablissementService.getCurrentUtilisateur();
        if (saisiPar != null) conduite.setSaisieParId(saisiPar.getId());
        conduiteRepository.save(conduite);
        return "redirect:/bulletins/" + eleveId + "?trimestre=" + trimestre;
    }
}
