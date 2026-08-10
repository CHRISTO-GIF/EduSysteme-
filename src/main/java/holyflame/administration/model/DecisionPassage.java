package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Decision de fin d'annee (brouillon puis definitive) pour un eleve : Admis / Redouble / Sort.
 * Persistee separement de l'execution reelle du passage (Eleve.classe) pour permettre un
 * flux en deux temps : decider eleve par eleve, puis "Cloturer la deliberation" qui applique
 * tout d'un coup une fois 100% des decisions prises.
 */
@Entity
@Table(name = "decisions_passage")
public class DecisionPassage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eleve_id", nullable = false)
    private Eleve eleve;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classe_origine_id", nullable = false)
    private Classe classeOrigine;

    private String anneeScolaire;
    private String decision; // ADMIS, REDOUBLE, SORT
    private Double moyenne;  // moyenne annuelle au moment de la decision (indicatif)
    private LocalDate dateDecision;
    private Long decideParId;
    private Long etablissementId;

    public DecisionPassage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
    public Classe getClasseOrigine() { return classeOrigine; }
    public void setClasseOrigine(Classe classeOrigine) { this.classeOrigine = classeOrigine; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public Double getMoyenne() { return moyenne; }
    public void setMoyenne(Double moyenne) { this.moyenne = moyenne; }
    public LocalDate getDateDecision() { return dateDecision; }
    public void setDateDecision(LocalDate dateDecision) { this.dateDecision = dateDecision; }
    public Long getDecideParId() { return decideParId; }
    public void setDecideParId(Long decideParId) { this.decideParId = decideParId; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
