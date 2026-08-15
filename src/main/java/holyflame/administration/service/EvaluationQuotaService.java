package holyflame.administration.service;

import holyflame.administration.model.Note;
import holyflame.administration.model.Parametre;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.ParametreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Garde-fou pedagogique (pas une contrainte de base de donnees) : une matiere ne devrait
 * recevoir qu'un nombre limite de devoirs par trimestre (2 ou 3, configurable par
 * etablissement) et un seul examen. Verifie ce quota avant l'enregistrement d'une NOUVELLE
 * evaluation (date inedite) — la mise a jour d'une evaluation deja saisie n'est jamais
 * bloquee. Renvoie un avertissement plutot qu'une exception : l'appelant decide de bloquer
 * ou de laisser confirmer.
 */
@Service
public class EvaluationQuotaService {

    private static final int QUOTA_EXAMENS = 1;
    private static final int QUOTA_DEVOIRS_DEFAUT = 2;
    private static final List<String> TYPES_DEVOIRS = List.of(Note.TYPE_DEVOIR, Note.TYPE_DEVOIR2, Note.TYPE_DEVOIR3);
    private static final List<String> TYPES_EXAMEN = List.of(Note.TYPE_EXAMEN);

    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private ParametreRepository parametreRepository;

    public int quotaDevoirs(Long etabId) {
        return parametreRepository.findByCleAndEtablissementId("NB_DEVOIRS_PAR_TRIMESTRE", etabId)
                .map(Parametre::getValeur)
                .map(v -> {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        return QUOTA_DEVOIRS_DEFAUT;
                    }
                })
                .orElse(QUOTA_DEVOIRS_DEFAUT);
    }

    /**
     * @return un message d'avertissement si l'enregistrement de cette evaluation (matiere +
     * classe + trimestre + type + date) depasserait le quota autorise, ou null si c'est bon.
     */
    public String verifierQuota(Long matiereId, Long classeId, Integer trimestre, String anneeScolaire,
            String type, LocalDate dateEvaluation, Long etabId) {
        boolean estExamen = Note.isExamenType(type);
        boolean estDevoir = Note.isDevoirLikeType(type);
        if (!estExamen && !estDevoir)
            return null;
        if (matiereId == null || classeId == null || trimestre == null || anneeScolaire == null)
            return null;

        List<String> typesRecherches = estExamen ? TYPES_EXAMEN : TYPES_DEVOIRS;
        List<LocalDate> datesExistantes = noteRepository.findDatesEvaluationsParMatiereClasseTrimestre(
                matiereId, classeId, trimestre, anneeScolaire, typesRecherches);

        boolean estNouvelleEvaluation = dateEvaluation == null || !datesExistantes.contains(dateEvaluation);
        if (!estNouvelleEvaluation)
            return null;

        int quota = estExamen ? QUOTA_EXAMENS : quotaDevoirs(etabId);
        if (datesExistantes.size() < quota)
            return null;

        return estExamen
                ? "Un examen est déjà enregistré pour cette matière ce trimestre — un seul examen est normalement autorisé."
                : datesExistantes.size() + " devoir(s) déjà enregistré(s) pour cette matière ce trimestre "
                        + "(maximum habituel : " + quota + ").";
    }
}
