package holyflame.administration.service;

import holyflame.administration.model.Absence;
import holyflame.administration.model.Conduite;
import holyflame.administration.model.DocumentEleve;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Note;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.AbsenceRepository;
import holyflame.administration.repository.ConduiteRepository;
import holyflame.administration.repository.DocumentEleveRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcule les donnees completes d'un bulletin (notes/poles, moyenne, rang, moyenne de classe,
 * assiduite, conduite, photo, professeur titulaire...) pour un eleve et un trimestre donnes.
 * Partage entre l'affichage staff/parent (/bulletins/{id}) et l'affichage eleve (/portail/bulletin)
 * afin que les deux flux produisent exactement le meme format de document.
 */
@Service
public class BulletinService {

    private static final List<String> ORDRE_POLES = List.of("SCIENTIFIQUE", "LITTERAIRE", "ARTS_SPORT", "AUTRE");

    @Autowired private EleveRepository eleveRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ConduiteRepository conduiteRepository;
    @Autowired private DocumentEleveRepository documentEleveRepository;
    @Autowired private AbsenceRepository absenceRepository;

    public String getMention(double moyenne) {
        if (moyenne >= 16) return "TRÈS BIEN";
        if (moyenne >= 14) return "BIEN";
        if (moyenne >= 12) return "ASSEZ BIEN";
        if (moyenne >= 10) return "PASSABLE";
        return "INSUFFISANT";
    }

    public String getAppreciation(double moyenne) {
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

    public Map<String, Object> calculerBulletin(Eleve eleve, Integer trimestre, Long etabId) {
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

            // ── Repartition controle continu (devoirs/participation) vs examens, pour l'affichage tableau de bord eleve ──
            List<Note> notesControleContinu = notesMatiere.stream()
                .filter(n -> !"EXAMEN".equals(n.getType())).toList();
            List<Note> notesExamens = notesMatiere.stream()
                .filter(n -> "EXAMEN".equals(n.getType())).toList();
            Double moyenneControleContinu = notesControleContinu.isEmpty() ? null
                : Math.round(moyennePonderee(notesControleContinu) * 100.0) / 100.0;
            Double moyenneExamens = notesExamens.isEmpty() ? null
                : Math.round(moyennePonderee(notesExamens) * 100.0) / 100.0;

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("matiere", matiere);
            ligne.put("moyenne", Math.round(moyMatiere * 100.0) / 100.0);
            ligne.put("moyenneClasse", Math.round(moyClasseMatiere * 100.0) / 100.0);
            ligne.put("min", Math.round(minClasse * 100.0) / 100.0);
            ligne.put("max", Math.round(maxClasse * 100.0) / 100.0);
            ligne.put("appreciation", appreciationMatiere);
            ligne.put("moyenneControleContinu", moyenneControleContinu);
            ligne.put("moyenneExamens", moyenneExamens);
            ligne.put("mention", getMention(moyMatiere));

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

        // ── Photo reelle de l'eleve si deja uploadee lors de l'inscription ──
        String photoPath = documentEleveRepository.findByEleveIdOrderByDateUploadDesc(eleve.getId()).stream()
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
        List<Absence> absences = absenceRepository.findByEleveIdOrderByDateDesc(eleve.getId());
        long absencesJustifiees = absences.stream().filter(Absence::isEstJustifiee).count();
        long absencesNonJustifiees = absences.size() - absencesJustifiees;

        // ── Distinctions reelles calculees ──
        boolean felicitations = moyenneArrondie >= 16;
        boolean tableauHonneur = rang <= 3;

        String codeVerification = "ETAB" + etabId + "-EL" + eleve.getId() + "-T" + trimestre + "-" + (anneeScolaire != null ? anneeScolaire.replace("-", "") : "");

        Map<String, Object> donnees = new LinkedHashMap<>();
        donnees.put("eleve", eleve);
        donnees.put("poles", polesOrdonnes);
        donnees.put("moyenneGenerale", moyenneArrondie);
        donnees.put("moyenneClasseGenerale", Math.round(moyenneClasseGenerale * 100.0) / 100.0);
        donnees.put("mention", getMention(moyenneArrondie));
        donnees.put("appreciation", getAppreciation(moyenneArrondie));
        donnees.put("rang", rang);
        donnees.put("effectif", effectif);
        donnees.put("professeurTitulaire", professeurTitulaire);
        donnees.put("photoPath", photoPath);
        donnees.put("conduite", conduite);
        donnees.put("absencesJustifiees", absencesJustifiees);
        donnees.put("absencesNonJustifiees", absencesNonJustifiees);
        donnees.put("felicitations", felicitations);
        donnees.put("tableauHonneur", tableauHonneur);
        donnees.put("codeVerification", codeVerification);
        donnees.put("anneeScolaire", anneeScolaire);
        return donnees;
    }
}
