package holyflame.administration.service;

public class AnneeScolaireClotureeException extends RuntimeException {
    private final String anneeScolaire;

    public AnneeScolaireClotureeException(String anneeScolaire) {
        super("L'annee scolaire " + anneeScolaire + " est cloturee.");
        this.anneeScolaire = anneeScolaire;
    }

    public String getAnneeScolaire() { return anneeScolaire; }
}
