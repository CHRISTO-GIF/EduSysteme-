package holyflame.administration.config;

import holyflame.administration.model.Etablissement;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Injecte automatiquement des attributs communs (ex: couleur d'accentuation de
 * l'etablissement) dans le modele de CHAQUE page rendue, sans que chaque
 * controleur ait besoin de le faire lui-meme.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private static final String COULEUR_PAR_DEFAUT = "#00236f";

    @Autowired private EtablissementService etablissementService;

    @ModelAttribute("accentColor")
    public String accentColor() {
        try {
            Etablissement etab = etablissementService.getCurrentEtablissement();
            if (etab != null && etab.getCouleurPrimaire() != null && !etab.getCouleurPrimaire().isBlank()) {
                return etab.getCouleurPrimaire();
            }
        } catch (Exception ignored) {
            // Utilisateur non authentifie (ex: page de login) : couleur par defaut.
        }
        return COULEUR_PAR_DEFAUT;
    }
}
