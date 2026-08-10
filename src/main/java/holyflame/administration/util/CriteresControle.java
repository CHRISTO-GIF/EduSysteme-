package holyflame.administration.util;

import java.util.List;

/**
 * Referentiel fixe des criteres de la "Fiche individuelle de controle"
 * utilisee par le coordonnateur pedagogique lors d'une visite de classe.
 */
public final class CriteresControle {

    public record Critere(String section, String libelle) {}

    public static final List<Critere> LISTE = List.of(
        new Critere("Organisation materiel", "Placement des eleves"),
        new Critere("Organisation materiel", "Placement du bureau"),
        new Critere("Organisation materiel", "Proprete de la salle"),
        new Critere("Organisation materiel", "Decoration de la salle"),
        new Critere("Organisation materiel", "Tableau d'affichage"),
        new Critere("Organisation materiel", "Presence de poubelle"),

        new Critere("Climat de la classe", "Relation maitre-eleve et eleve-eleve (ordre, discipline, niveau d'attention, confiance des eleves)"),
        new Critere("Climat de la classe", "Utilisation de la chicotte"),
        new Critere("Climat de la classe", "Les eleves se communiquent-ils entre eux ?"),
        new Critere("Climat de la classe", "Les eleves ecoutent-ils le maitre ?"),
        new Critere("Climat de la classe", "Les eleves agissent-ils ?"),
        new Critere("Climat de la classe", "Les eleves respectent-ils le maitre ?"),
        new Critere("Climat de la classe", "Les eleves s'expriment-ils ? (questions, reponses, avis)"),
        new Critere("Climat de la classe", "Les eleves respectent-ils les consignes ?"),

        new Critere("L'enseignant", "Tenue et voix du maitre"),
        new Critere("L'enseignant", "Dynamisme"),
        new Critere("L'enseignant", "Organisation du tableau"),
        new Critere("L'enseignant", "Clarte des consignes"),
        new Critere("L'enseignant", "Clarte des informations"),
        new Critere("L'enseignant", "Respect des eleves"),

        new Critere("Demarche pedagogique", "Le langage est-il adapte aux eleves ?"),
        new Critere("Demarche pedagogique", "Qualite de la preparation de la classe"),
        new Critere("Demarche pedagogique", "Suivi des programmes"),
        new Critere("Demarche pedagogique", "Pertinence et clarte des objectifs"),
        new Critere("Demarche pedagogique", "Utilisation d'une situation probleme"),
        new Critere("Demarche pedagogique", "Rigueur du contenu"),
        new Critere("Demarche pedagogique", "Enchainement logique"),
        new Critere("Demarche pedagogique", "Materiel didactique"),
        new Critere("Demarche pedagogique", "Travail en groupe")
    );

    private CriteresControle() {}
}
