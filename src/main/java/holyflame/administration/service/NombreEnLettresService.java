package holyflame.administration.service;

import org.springframework.stereotype.Service;

@Service
public class NombreEnLettresService {

    private static final String[] UNITES = {
        "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
        "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize",
        "dix-sept", "dix-huit", "dix-neuf"
    };
    private static final String[] DIZAINES = {
        "", "", "vingt", "trente", "quarante", "cinquante", "soixante", "soixante-dix", "quatre-vingt", "quatre-vingt-dix"
    };

    /** Convertit un montant entier en toutes lettres, en francais, suivi de l'unite monetaire fournie. */
    public String convertir(long montant, String monnaie) {
        if (montant == 0) return "zéro " + monnaie;
        String lettres = convertirEntier(montant).trim();
        return capitaliser(lettres) + " " + monnaie;
    }

    private String convertirEntier(long n) {
        if (n < 0) return "moins " + convertirEntier(-n);
        if (n < 20) return UNITES[(int) n];
        if (n < 100) return convertirDizaine((int) n);
        if (n < 1000) return convertirCentaine((int) n);
        if (n < 1_000_000) return convertirMillier(n);
        if (n < 1_000_000_000) return convertirMillion(n);
        return convertirMilliard(n);
    }

    private String convertirDizaine(int n) {
        int d = n / 10;
        int u = n % 10;
        if (d == 7 || d == 9) {
            // soixante-dix / quatre-vingt-dix : la base "dizaine" reste 60 / 80, l'unite devient 10-19
            String base = DIZAINES[d - 1];
            return base + (u == 1 && d == 7 ? "-et-onze" : "-" + UNITES[10 + u]);
        }
        String base = DIZAINES[d];
        if (u == 0) return base + (d == 8 ? "s" : "");
        if (u == 1 && (d == 2 || d == 3 || d == 4 || d == 5 || d == 6)) return base + "-et-un";
        return base + "-" + UNITES[u];
    }

    private String convertirCentaine(int n) {
        int c = n / 100;
        int reste = n % 100;
        String prefixe = (c == 1 ? "cent" : UNITES[c] + " cent" + (reste == 0 ? "s" : ""));
        if (reste == 0) return prefixe;
        return prefixe + " " + convertirEntier(reste);
    }

    private String convertirMillier(long n) {
        long milliers = n / 1000;
        long reste = n % 1000;
        String prefixe = milliers == 1 ? "mille" : convertirEntier(milliers) + " mille";
        if (reste == 0) return prefixe;
        return prefixe + " " + convertirEntier(reste);
    }

    private String convertirMillion(long n) {
        long millions = n / 1_000_000;
        long reste = n % 1_000_000;
        String prefixe = convertirEntier(millions) + " million" + (millions > 1 ? "s" : "");
        if (reste == 0) return prefixe;
        return prefixe + " " + convertirEntier(reste);
    }

    private String convertirMilliard(long n) {
        long milliards = n / 1_000_000_000;
        long reste = n % 1_000_000_000;
        String prefixe = convertirEntier(milliards) + " milliard" + (milliards > 1 ? "s" : "");
        if (reste == 0) return prefixe;
        return prefixe + " " + convertirEntier(reste);
    }

    private String capitaliser(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
