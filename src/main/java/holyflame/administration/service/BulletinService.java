package holyflame.administration.service;

import holyflame.administration.model.Absence;
import holyflame.administration.model.Conduite;
import holyflame.administration.model.DocumentEleve;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Note;
import holyflame.administration.model.Parametre;
import holyflame.administration.model.PeriodeCalendrier;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.AbsenceRepository;
import holyflame.administration.repository.ConduiteRepository;
import holyflame.administration.repository.DocumentEleveRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.repository.PeriodeCalendrierRepository;
import holyflame.administration.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcule les donnees completes d'un bulletin (notes/poles, moyenne, rang,
 * moyenne de classe,
 * assiduite, conduite, photo, professeur titulaire...) pour un eleve et un
 * trimestre donnes.
 * Partage entre l'affichage staff/parent (/bulletins/{id}) et l'affichage eleve
 * (/portail/bulletin)
 * afin que les deux flux produisent exactement le meme format de document.
 */
@Service
public class BulletinService {

    private static final List<String> ORDRE_POLES = List.of("SCIENTIFIQUE", "LITTERAIRE", "ARTS_SPORT", "AUTRE");

    @Autowired
    private EleveRepository eleveRepository;
    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private UtilisateurRepository utilisateurRepository;
    @Autowired
    private ConduiteRepository conduiteRepository;
    @Autowired
    private DocumentEleveRepository documentEleveRepository;
    @Autowired
    private AbsenceRepository absenceRepository;
    @Autowired
    private PeriodeCalendrierRepository periodeCalendrierRepository;
    @Autowired
    private ParametreRepository parametreRepository;

    public String getMention(double moyenne) {
        if (moyenne >= 16)
            return "TRÈS BIEN";
        if (moyenne >= 14)
            return "BIEN";
        if (moyenne >= 12)
            return "ASSEZ BIEN";
        if (moyenne >= 10)
            return "PASSABLE";
        return "INSUFFISANT";
    }

    public String getAppreciation(double moyenne) {
        if (moyenne >= 18)
            return "Résultats exceptionnels. Félicitations du conseil de classe.";
        if (moyenne >= 16)
            return "Excellents résultats. Toutes nos félicitations.";
        if (moyenne >= 14)
            return "Très bons résultats. Encouragements du conseil de classe.";
        if (moyenne >= 12)
            return "Bons résultats. Peut encore progresser.";
        if (moyenne >= 10)
            return "Résultats satisfaisants. Des efforts restent nécessaires.";
        if (moyenne >= 8)
            return "Résultats insuffisants. Un travail sérieux s'impose.";
        return "Résultats très insuffisants. Une remise en question est nécessaire.";
    }

    private String libellePole(String pole) {
        if ("SCIENTIFIQUE".equals(pole))
            return "Pôle Scientifique";
        if ("LITTERAIRE".equals(pole))
            return "Pôle Littéraire & Langues";
        if ("ARTS_SPORT".equals(pole))
            return "Pôle Arts & Sport";
        return "Autres Matières";
    }

    /**
     * Moyenne ponderee d'un eleve pour une liste de notes deja filtrees (trimestre
     * + statut publie), toutes rattachees a la MEME matiere : pondere chaque
     * evaluation (devoir/examen) par son propre coefficient. Reste utilisee pour les
     * moyennes d'AFFICHAGE par type (ex: "Devoir 2 : 14/20" quand plusieurs notes
     * partagent ce type) — pas pour la moyenne academique officielle, voir
     * {@link #calculerMoyenneMatiere}.
     */
    private double moyennePonderee(List<Note> notes) {
        double somme = notes.stream().filter(n -> n.getValeur() != null && n.getCoefficient() != null)
                .mapToDouble(n -> n.getValeur() * n.getCoefficient()).sum();
        double coef = notes.stream().filter(n -> n.getCoefficient() != null)
                .mapToDouble(Note::getCoefficient).sum();
        return coef > 0 ? somme / coef : 0;
    }

    private double parametreDouble(Long etabId, String cle, double defaut) {
        if (etabId == null)
            return defaut;
        return parametreRepository.findByCleAndEtablissementId(cle, etabId)
                .map(Parametre::getValeur)
                .map(v -> {
                    try {
                        return Double.parseDouble(v.trim());
                    } catch (NumberFormatException e) {
                        return defaut;
                    }
                })
                .orElse(defaut);
    }

    /**
     * Bonus Participation &amp; Travaux Pratiques : recompense l'initiative sans jamais
     * penaliser un eleve qui n'a pas (encore) de note dans ces categories. Chaque note de
     * participation/TP dont la valeur atteint le seuil configure ajoute un petit bonus,
     * plafonne pour qu'il reste un coup de pouce et non un second examen deguise. Reglable
     * par etablissement dans Parametres (BONUS_PARTICIPATION_TP_SEUIL / _PAR_NOTE / _MAX).
     */
    private double bonusParticipationTP(List<Note> notesMatiere, Long etabId) {
        double seuil = parametreDouble(etabId, "BONUS_PARTICIPATION_TP_SEUIL", 14.0);
        double parNote = parametreDouble(etabId, "BONUS_PARTICIPATION_TP_PAR_NOTE", 0.25);
        double plafond = parametreDouble(etabId, "BONUS_PARTICIPATION_TP_MAX", 1.0);
        long nbNotesBonus = notesMatiere.stream()
                .filter(n -> Note.isBonusType(n.getType()) && n.getValeur() != null && n.getValeur() >= seuil)
                .count();
        return Math.min(plafond, nbNotesBonus * parNote);
    }

    /**
     * Moyenne academique officielle d'une matiere pour un trimestre, en deux etapes :
     * 1) Moyenne des devoirs = moyenne arithmetique simple des DEV1/DEV2/DEV3 reellement
     *    enregistres (2 ou 3 selon le cas — jamais une division fixe par 3 : un devoir
     *    non saisi n'est jamais compte comme un zero).
     * 2) Cette moyenne des devoirs et la note d'examen comptent chacune pour moitie, pour
     *    que le poids du controle continu face a l'examen reste stable qu'il y ait 2 ou 3
     *    devoirs saisis (contrairement a une simple moyenne ponderee globale, ou l'ajout
     *    d'un devoir supplementaire diluerait mecaniquement le poids de l'examen).
     * Participation et Travaux Pratiques n'entrent jamais dans ce calcul : ils s'ajoutent
     * ensuite en bonus plafonne (voir {@link #bonusParticipationTP}), pour encourager
     * l'initiative sans jamais faire baisser la moyenne d'un eleve qui n'y a pas participe.
     */
    private double calculerMoyenneMatiere(List<Note> notesMatiere, Long etabId) {
        List<Double> valeursDevoirs = notesMatiere.stream()
                .filter(n -> Note.isDevoirLikeType(n.getType()) && n.getValeur() != null)
                .map(Note::getValeur)
                .toList();
        Double moyenneDevoirs = valeursDevoirs.isEmpty() ? null
                : valeursDevoirs.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        List<Double> valeursExamen = notesMatiere.stream()
                .filter(n -> Note.isExamenType(n.getType()) && n.getValeur() != null)
                .map(Note::getValeur)
                .toList();
        Double moyenneExamen = valeursExamen.isEmpty() ? null
                : valeursExamen.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        Double moyenneAcademique;
        if (moyenneDevoirs != null && moyenneExamen != null) {
            moyenneAcademique = (moyenneDevoirs + moyenneExamen) / 2.0;
        } else if (moyenneDevoirs != null) {
            moyenneAcademique = moyenneDevoirs;
        } else if (moyenneExamen != null) {
            moyenneAcademique = moyenneExamen;
        } else {
            moyenneAcademique = null;
        }
        if (moyenneAcademique == null)
            return 0;

        return Math.min(20.0, moyenneAcademique + bonusParticipationTP(notesMatiere, etabId));
    }

    /**
     * Moyenne generale ponderee par le coefficient de CHAQUE matiere (ex: Maths
     * coef 4 pese plus que Sport coef 1). Sans ce regroupement prealable par
     * matiere, une matiere avec beaucoup de petites evaluations ecraserait les
     * autres independamment du coefficient reellement configure. Une matiere sans
     * aucun devoir ni examen enregistre (meme si elle a une note de participation
     * isolee) n'entre pas dans la moyenne — pas encore assez de matiere academique
     * a evaluer.
     */
    private double moyenneGeneraleParMatiere(List<Note> notes, Long etabId) {
        Map<Long, List<Note>> parMatiere = new LinkedHashMap<>();
        for (Note n : notes) {
            if (n.getMatiere() == null)
                continue;
            parMatiere.computeIfAbsent(n.getMatiere().getId(), k -> new ArrayList<>()).add(n);
        }
        double sommePonderee = 0;
        double sommeCoef = 0;
        for (List<Note> notesMatiere : parMatiere.values()) {
            boolean aDeLaMatiereAcademique = notesMatiere.stream()
                    .anyMatch(n -> (Note.isDevoirLikeType(n.getType()) || Note.isExamenType(n.getType()))
                            && n.getValeur() != null);
            if (!aDeLaMatiereAcademique)
                continue;
            Matiere matiere = notesMatiere.get(0).getMatiere();
            double coefMatiere = matiere.getCoefficient() != null ? matiere.getCoefficient() : 1.0;
            sommePonderee += calculerMoyenneMatiere(notesMatiere, etabId) * coefMatiere;
            sommeCoef += coefMatiere;
        }
        return sommeCoef > 0 ? sommePonderee / sommeCoef : 0;
    }

    /**
     * Notes publiees d'un eleve pour un trimestre et une annee scolaire donnee
     * (evite de melanger les notes de plusieurs annees apres un passage de classe).
     * L'annee scolaire est desormais obligatoire a la saisie : les notes qui en
     * seraient depourvues n'existent plus en base (purgees au demarrage).
     */
    private List<Note> notesEleveTrimestreAnnee(Eleve eleve, Integer trimestre, String anneeScolaire) {
        List<Note> notes = anneeScolaire != null
                ? noteRepository.findByEleveAndTrimestreAndAnneeScolaireOrderByMatiereNomAsc(eleve, trimestre,
                        anneeScolaire)
                : noteRepository.findByEleveAndTrimestreOrderByMatiereNomAsc(eleve, trimestre);
        return notes.stream().filter(n -> !"BROUILLON".equals(n.getStatut())).toList();
    }

    /**
     * Absences d'un eleve limitees aux dates du trimestre demande, via la periode de
     * calendrier scolaire (Parametres > Calendrier) correspondante. Si aucune periode
     * TRIMESTRE{n} n'est configuree pour cette annee, on retombe sur l'historique complet
     * de l'eleve : mieux vaut un chiffre potentiellement trop large qu'un zero trompeur.
     */
    public List<Absence> absencesDuTrimestre(Eleve eleve, Integer trimestre, String anneeScolaire, Long etabId) {
        List<Absence> toutesLesAbsences = absenceRepository.findByEleveIdOrderByDateDesc(eleve.getId());
        if (anneeScolaire == null || trimestre == null || etabId == null)
            return toutesLesAbsences;
        String typePeriode = "TRIMESTRE" + trimestre;
        return periodeCalendrierRepository.findByEtablissementIdAndAnneeScolaireOrderByDateDebutAsc(etabId, anneeScolaire)
                .stream()
                .filter(p -> typePeriode.equals(p.getType()))
                .findFirst()
                .<List<Absence>>map(periode -> toutesLesAbsences.stream()
                        .filter(a -> a.getDate() != null
                                && !a.getDate().isBefore(periode.getDateDebut())
                                && !a.getDate().isAfter(periode.getDateFin()))
                        .toList())
                .orElse(toutesLesAbsences);
    }

    /**
     * Moyenne annuelle (moyenne des 3 trimestres publies) pour le module de passage
     * de classe.
     */
    public double calculerMoyenneAnnuelle(Eleve eleve, String anneeScolaire) {
        List<Double> moyennesTrimestres = new ArrayList<>();
        for (int t = 1; t <= 3; t++) {
            List<Note> notes = notesEleveTrimestreAnnee(eleve, t, anneeScolaire);
            if (!notes.isEmpty())
                moyennesTrimestres.add(moyenneGeneraleParMatiere(notes, eleve.getEtablissementId()));
        }
        return moyennesTrimestres.isEmpty() ? 0
                : moyennesTrimestres.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public Map<String, Object> calculerBulletin(Eleve eleve, Integer trimestre, Long etabId) {
        String anneeScolaireBulletin = eleve.getClasse() != null ? eleve.getClasse().getAnneeScolaire() : null;
        List<Note> notes = notesEleveTrimestreAnnee(eleve, trimestre, anneeScolaireBulletin);
        double moyenne = moyenneGeneraleParMatiere(notes, etabId);
        double moyenneArrondie = Math.round(moyenne * 100.0) / 100.0;

        // ── Rang, moyenne de classe et stats reelles par matiere (calculees sur tous
        // les camarades) ──
        int rang = 1;
        int effectif = 1;
        double moyenneClasseGenerale = 0;
        Map<Long, List<Double>> moyennesParMatiereClasse = new LinkedHashMap<>();
        if (eleve.getClasse() != null) {
            List<Eleve> camarades = eleveRepository.findByClasseIdOrderByNomAsc(eleve.getClasse().getId());
            effectif = camarades.size();
            double sommeMoyennes = 0;
            for (Eleve cam : camarades) {
                List<Note> notesCam = notesEleveTrimestreAnnee(cam, trimestre, anneeScolaireBulletin);
                double moyenneCam = moyenneGeneraleParMatiere(notesCam, etabId);
                sommeMoyennes += moyenneCam;
                if (!cam.getId().equals(eleve.getId()) && moyenneCam > moyenne)
                    rang++;

                Map<Long, List<Note>> parMatiereCam = new LinkedHashMap<>();
                for (Note n : notesCam) {
                    if (n.getMatiere() == null || n.getValeur() == null)
                        continue;
                    parMatiereCam.computeIfAbsent(n.getMatiere().getId(), k -> new ArrayList<>()).add(n);
                }
                for (Map.Entry<Long, List<Note>> e : parMatiereCam.entrySet()) {
                    double moyMatiereCam = calculerMoyenneMatiere(e.getValue(), etabId);
                    moyennesParMatiereClasse.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(moyMatiereCam);
                }
            }
            moyenneClasseGenerale = effectif > 0 ? sommeMoyennes / effectif : 0;
        }

        // ── Regroupement des notes de l'eleve par matiere, puis par pole ──
        Map<Long, List<Note>> notesParMatiere = new LinkedHashMap<>();
        for (Note n : notes) {
            if (n.getMatiere() == null)
                continue;
            notesParMatiere.computeIfAbsent(n.getMatiere().getId(), k -> new ArrayList<>()).add(n);
        }

        Map<String, List<Map<String, Object>>> poles = new LinkedHashMap<>();
        for (Map.Entry<Long, List<Note>> entry : notesParMatiere.entrySet()) {
            List<Note> notesMatiere = entry.getValue();
            Matiere matiere = notesMatiere.get(0).getMatiere();
            double moyMatiere = calculerMoyenneMatiere(notesMatiere, etabId);
            List<Double> moyClasseListe = moyennesParMatiereClasse.getOrDefault(entry.getKey(), List.of());
            double moyClasseMatiere = moyClasseListe.isEmpty() ? moyMatiere
                    : moyClasseListe.stream().mapToDouble(Double::doubleValue).average().orElse(moyMatiere);
            String appreciationMatiere = notesMatiere.stream()
                    .filter(n -> n.getCommentaire() != null && !n.getCommentaire().isBlank())
                    .reduce((first, second) -> second) // le plus recent (liste triee par date d'evaluation croissante
                                                       // en base)
                    .map(Note::getCommentaire).orElse("");

            // ── Repartition controle continu (devoirs/participation) vs examens, pour
            // l'affichage tableau de bord eleve ──
            List<Note> notesDevoir1 = notesMatiere.stream()
                    .filter(n -> Note.TYPE_DEVOIR.equals(Note.normalizeType(n.getType())))
                    .toList();
            List<Note> notesDevoir2 = notesMatiere.stream()
                    .filter(n -> Note.TYPE_DEVOIR2.equals(Note.normalizeType(n.getType())))
                    .toList();
            List<Note> notesDevoir3 = notesMatiere.stream()
                    .filter(n -> Note.TYPE_DEVOIR3.equals(Note.normalizeType(n.getType())))
                    .toList();
            List<Note> notesExamens = notesMatiere.stream()
                    .filter(n -> Note.isExamenType(n.getType()))
                    .toList();
            List<Note> notesControleContinuMatiere = notesMatiere.stream()
                    .filter(n -> Note.isControleContinuType(n.getType()))
                    .toList();
            List<Note> notesTP = notesMatiere.stream()
                    .filter(n -> Note.TYPE_TP.equals(Note.normalizeType(n.getType())))
                    .toList();
            List<Note> notesParticipation = notesMatiere.stream()
                    .filter(n -> Note.TYPE_PARTICIPATION.equals(Note.normalizeType(n.getType())))
                    .toList();
            List<Note> notesDevoirsTous = notesMatiere.stream()
                    .filter(n -> Note.isDevoirLikeType(n.getType()) && n.getValeur() != null)
                    .toList();
            Double moyenneDevoir1 = notesDevoir1.isEmpty() ? null
                    : Math.round(moyennePonderee(notesDevoir1) * 100.0) / 100.0;
            Double moyenneDevoir2 = notesDevoir2.isEmpty() ? null
                    : Math.round(moyennePonderee(notesDevoir2) * 100.0) / 100.0;
            Double moyenneDevoir3 = notesDevoir3.isEmpty() ? null
                    : Math.round(moyennePonderee(notesDevoir3) * 100.0) / 100.0;
            Double moyenneExamens = notesExamens.isEmpty() ? null
                    : Math.round(moyennePonderee(notesExamens) * 100.0) / 100.0;
            Double moyenneControleContinuMatiere = notesControleContinuMatiere.isEmpty() ? null
                    : Math.round(moyennePonderee(notesControleContinuMatiere) * 100.0) / 100.0;
            // Moyenne des devoirs reellement enregistres (2 ou 3, jamais une division fixe) —
            // c'est ce chiffre, pas moyenneDevoir1/2/3 pris isolement, qui pese pour moitie
            // dans le calcul de la moyenne officielle de la matiere.
            Double moyenneDevoirs = notesDevoirsTous.isEmpty() ? null
                    : Math.round(notesDevoirsTous.stream().mapToDouble(Note::getValeur).average().orElse(0) * 100.0)
                            / 100.0;
            double bonusMatiere = Math.round(bonusParticipationTP(notesMatiere, etabId) * 100.0) / 100.0;

            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("matiere", matiere);
            ligne.put("moyenne", Math.round(moyMatiere * 100.0) / 100.0);
            ligne.put("moyenneClasse", Math.round(moyClasseMatiere * 100.0) / 100.0);
            ligne.put("appreciation", appreciationMatiere);
            ligne.put("moyenneDevoir1", moyenneDevoir1);
            ligne.put("moyenneDevoir2", moyenneDevoir2);
            ligne.put("moyenneDevoir3", moyenneDevoir3);
            ligne.put("moyenneDevoirs", moyenneDevoirs);
            ligne.put("nbDevoirsSaisis", notesDevoirsTous.size());
            ligne.put("moyenneExamens", moyenneExamens);
            ligne.put("moyenneControleContinu", moyenneControleContinuMatiere);
            ligne.put("aParticipation", !notesParticipation.isEmpty());
            ligne.put("aTP", !notesTP.isEmpty());
            ligne.put("bonus", bonusMatiere > 0 ? bonusMatiere : null);
            ligne.put("mention", getMention(moyMatiere));

            String pole = matiere.getPole() != null ? matiere.getPole() : "AUTRE";
            poles.computeIfAbsent(pole, k -> new ArrayList<>()).add(ligne);
        }
        // Reordonner selon l'ordre de poles souhaite
        Map<String, List<Map<String, Object>>> polesOrdonnes = new LinkedHashMap<>();
        for (String p : ORDRE_POLES) {
            if (poles.containsKey(p))
                polesOrdonnes.put(libellePole(p), poles.get(p));
        }
        poles.forEach((k, v) -> {
            if (!ORDRE_POLES.contains(k))
                polesOrdonnes.put(libellePole(k), v);
        });

        // ── Professeur titulaire ──
        Utilisateur professeurTitulaire = null;
        if (eleve.getClasse() != null && eleve.getClasse().getProfesseurTitulaireId() != null) {
            professeurTitulaire = utilisateurRepository.findById(eleve.getClasse().getProfesseurTitulaireId())
                    .orElse(null);
        }

        // ── Photo reelle de l'eleve si deja uploadee lors de l'inscription ──
        String photoPath = documentEleveRepository.findByEleveIdOrderByDateUploadDesc(eleve.getId()).stream()
                .filter(d -> "PHOTO_IDENTITE".equals(d.getTypeDocument()))
                .findFirst()
                .map(DocumentEleve::getCheminFichier)
                .orElse(null);

        // ── Conduite reelle (si saisie) ──
        String anneeScolaire = anneeScolaireBulletin;
        Conduite conduite = anneeScolaire != null
                ? conduiteRepository.findByEleveAndTrimestreAndAnneeScolaire(eleve, trimestre, anneeScolaire)
                        .orElse(null)
                : null;

        // ── Assiduite reelle, limitee aux dates du trimestre consulte (via le calendrier
        // scolaire configure dans Parametres > Calendrier). Sans periode de trimestre definie
        // pour cette annee, on retombe sur l'historique complet plutot que d'afficher zero. ──
        List<Absence> absences = absencesDuTrimestre(eleve, trimestre, anneeScolaireBulletin, etabId);
        long absencesJustifiees = absences.stream().filter(Absence::isEstJustifiee).count();
        long absencesNonJustifiees = absences.size() - absencesJustifiees;

        // ── Distinctions reelles calculees ──
        boolean felicitations = moyenneArrondie >= 16;
        boolean tableauHonneur = rang <= 3;

        List<Note> notesControleContinu = notes.stream()
                .filter(n -> Note.isControleContinuType(n.getType()))
                .toList();
        List<Note> notesExamens = notes.stream()
                .filter(n -> Note.isExamenType(n.getType()))
                .toList();
        Double moyenneControleContinu = notesControleContinu.isEmpty() ? null
                : Math.round(moyenneGeneraleParMatiere(notesControleContinu, etabId) * 100.0) / 100.0;
        Double moyenneExamens = notesExamens.isEmpty() ? null
                : Math.round(moyenneGeneraleParMatiere(notesExamens, etabId) * 100.0) / 100.0;

        String codeVerification = "ETAB" + etabId + "-EL" + eleve.getId() + "-T" + trimestre + "-"
                + (anneeScolaire != null ? anneeScolaire.replace("-", "") : "");

        Map<String, Object> donnees = new LinkedHashMap<>();
        donnees.put("eleve", eleve);
        donnees.put("poles", polesOrdonnes);
        donnees.put("moyenneGenerale", moyenneArrondie);
        donnees.put("moyenneClasseGenerale", Math.round(moyenneClasseGenerale * 100.0) / 100.0);
        donnees.put("moyenneControleContinu", moyenneControleContinu);
        donnees.put("moyenneExamens", moyenneExamens);
        donnees.put("mention", getMention(moyenneArrondie));
        donnees.put("appreciation", notes.isEmpty()
                ? "Aucune note n'a encore été publiée pour ce trimestre."
                : getAppreciation(moyenneArrondie));
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
