package holyflame.administration.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class WhatsAppUtil {

    private WhatsAppUtil() {}

    /** Nettoie un numero (garde uniquement les chiffres) pour le format attendu par wa.me. */
    public static String nettoyerNumero(String telephone) {
        if (telephone == null) return null;
        String chiffres = telephone.replaceAll("[^0-9]", "");
        return chiffres.isBlank() ? null : chiffres;
    }

    /** Construit un lien wa.me pret a l'emploi, ou null si le numero est absent/invalide. */
    public static String lien(String telephone, String message) {
        String numero = nettoyerNumero(telephone);
        if (numero == null) return null;
        String texte = message != null ? URLEncoder.encode(message, StandardCharsets.UTF_8) : "";
        return "https://wa.me/" + numero + (texte.isBlank() ? "" : "?text=" + texte);
    }
}
