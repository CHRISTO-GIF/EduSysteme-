package holyflame.administration.service;

import holyflame.administration.model.CategorieComptable;
import holyflame.administration.repository.CategorieComptableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plan comptable standard (charges/produits) commun a tous les etablissements. Auparavant seede
 * une seule fois au demarrage pour le tout premier etablissement uniquement (DataInitializer),
 * ce qui laissait tout etablissement cree ensuite — a l'inscription ou via une nouvelle base —
 * sans aucune categorie comptable : impossible d'enregistrer une depense ou une ligne de budget.
 */
@Service
public class PlanComptableService {

    @Autowired private CategorieComptableRepository categorieComptableRepository;

    private record Poste(String code, String libelle, String sens, String groupe) {}

    private static final String CHARGE = "CHARGE";
    private static final String PRODUIT = "PRODUIT";
    private static final String FOURNITURES = "FOURNITURES_SCOLAIRES";
    private static final String ENTRETIEN = "ENTRETIEN_SALUBRITE";
    private static final String FONCTIONNEMENT = "FONCTIONNEMENT";
    private static final String INVESTISSEMENT = "INVESTISSEMENT";

    private static final Poste[] PLAN = {
        new Poste("21820", "Materiels de transports", CHARGE, INVESTISSEMENT),
        new Poste("21830", "Ordinateurs ou Imprimantes", CHARGE, INVESTISSEMENT),
        new Poste("21840", "Equipements", CHARGE, INVESTISSEMENT),
        new Poste("60110", "Electrique (materiel et reparation)", CHARGE, FONCTIONNEMENT),
        new Poste("60450", "Impressions et Informatique", CHARGE, FONCTIONNEMENT),
        new Poste("60460", "Produits d'entretien", CHARGE, ENTRETIEN),
        new Poste("60470", "Fourniture de bureau", CHARGE, FOURNITURES),
        new Poste("60472", "Agios", CHARGE, FONCTIONNEMENT),
        new Poste("60473", "Fournitures scolaires", CHARGE, FOURNITURES),
        new Poste("60530", "Gazoil groupe", CHARGE, FONCTIONNEMENT),
        new Poste("60531", "Eau", CHARGE, FONCTIONNEMENT),
        new Poste("60500", "SNE", CHARGE, FONCTIONNEMENT),
        new Poste("60510", "Plomberie", CHARGE, INVESTISSEMENT),
        new Poste("60580", "Mobilier (achat)", CHARGE, INVESTISSEMENT),
        new Poste("61412", "Deplacement, taxis", CHARGE, FONCTIONNEMENT),
        new Poste("62420", "Entretien mobilier", CHARGE, ENTRETIEN),
        new Poste("62430", "Entretien Batiment", CHARGE, INVESTISSEMENT),
        new Poste("62440", "Entretien groupe", CHARGE, FONCTIONNEMENT),
        new Poste("62650", "Bibliotheque, manuels", CHARGE, FOURNITURES),
        new Poste("62810", "Tel-Corr-Internet", CHARGE, FONCTIONNEMENT),
        new Poste("63840", "Voyages", CHARGE, FONCTIONNEMENT),
        new Poste("65130", "Retour de scolarite", CHARGE, FONCTIONNEMENT),
        new Poste("65820", "Reduction de la scolarite", CHARGE, FONCTIONNEMENT),
        new Poste("66120", "Salaires Vacataires", CHARGE, FONCTIONNEMENT),
        new Poste("66110", "Salaires Permanants", CHARGE, FONCTIONNEMENT),
        new Poste("66170", "Achat de Tenues", CHARGE, FOURNITURES),
        new Poste("66160", "Honoraires Cabinet d'avocat", CHARGE, FONCTIONNEMENT),
        new Poste("66161", "Salaires occasionnels", CHARGE, FONCTIONNEMENT),
        new Poste("66180", "Publicite", CHARGE, FONCTIONNEMENT),
        new Poste("66181", "Loisirs : enseignants", CHARGE, FONCTIONNEMENT),
        new Poste("66182", "Prelevement", CHARGE, FONCTIONNEMENT),
        new Poste("66183", "Loisir : des eleves", CHARGE, FONCTIONNEMENT),
        new Poste("66420", "Charges sociales (CNPS)", CHARGE, FONCTIONNEMENT),
        new Poste("66421", "Impot sur salaire", CHARGE, FONCTIONNEMENT),
        new Poste("66430", "Cotisations", CHARGE, FONCTIONNEMENT),
        new Poste("66431", "Accompagnement Administratif", CHARGE, FONCTIONNEMENT),
        new Poste("66840", "Medicaments, soins medicaux", CHARGE, FONCTIONNEMENT),
        new Poste("66841", "Assurances", CHARGE, FONCTIONNEMENT),
        new Poste("66850", "Depenses Examens", CHARGE, FOURNITURES),
        new Poste("66881", "Formation", CHARGE, FONCTIONNEMENT),
        new Poste("67500", "Loyer", CHARGE, FONCTIONNEMENT),
        new Poste("67600", "Depenses chantier", CHARGE, INVESTISSEMENT),
        new Poste("70110", "Scolarite", PRODUIT, null),
        new Poste("70720", "Subventions diverses", PRODUIT, null),
        new Poste("70730", "Locations : locaux, manuels", PRODUIT, null),
        new Poste("71824", "Dons d'organisme", PRODUIT, null),
        new Poste("71825", "Dons divers recus", PRODUIT, null),
        new Poste("72100", "Vente de Tenues de classe", PRODUIT, null),
        new Poste("75820", "APE", PRODUIT, null),
        new Poste("77100", "Ventes diverses", PRODUIT, null),
        new Poste("77110", "Arrieres scolarites", PRODUIT, null),
        new Poste("77120", "Test d'entree", PRODUIT, null),
        new Poste("77170", "Vente tenue de sport", PRODUIT, null),
        new Poste("77180", "Vente Pull over", PRODUIT, null),
        new Poste("77190", "Somme empruntee", PRODUIT, null),
        new Poste("77160", "Cahiers (Inscriptions + Test)", PRODUIT, null),
    };

    /** A appeler a la creation de tout etablissement, et defensivement avant tout affichage
        du module Finances : sans effet si l'etablissement a deja son plan comptable. */
    public void seedSiVide(Long etabId) {
        if (etabId == null || !categorieComptableRepository.findByEtablissementIdAndActifTrueOrderByCodeAsc(etabId).isEmpty()) {
            return;
        }
        for (Poste p : PLAN) {
            CategorieComptable c = new CategorieComptable();
            c.setCode(p.code());
            c.setLibelle(p.libelle());
            c.setSens(p.sens());
            c.setGroupe(p.groupe());
            c.setEtablissementId(etabId);
            categorieComptableRepository.save(c);
        }
    }
}
