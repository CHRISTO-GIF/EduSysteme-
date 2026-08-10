package holyflame.administration.util;

import java.time.LocalDate;

public final class AnneeScolaireUtil {

    private AnneeScolaireUtil() {}

    /** Ordre des mois d'une annee scolaire : Septembre a Aout. */
    public static final int[] MOIS_ORDRE_SCOLAIRE = {9, 10, 11, 12, 1, 2, 3, 4, 5, 6, 7, 8};

    public static String pour(LocalDate date) {
        return date.getMonthValue() >= 9
            ? date.getYear() + "-" + (date.getYear() + 1)
            : (date.getYear() - 1) + "-" + date.getYear();
    }

    /** Annee calendaire du mois donne au sein de l'annee scolaire "YYYY-YYYY+1" (ex: "2025-2026", mois=1 -> 2026). */
    public static int anneeCalendairePourMois(String anneeScolaire, int mois) {
        int anneeDebut;
        try {
            anneeDebut = Integer.parseInt(anneeScolaire.split("-")[0].trim());
        } catch (Exception e) {
            anneeDebut = LocalDate.now().getYear();
        }
        return mois >= 9 ? anneeDebut : anneeDebut + 1;
    }
}
