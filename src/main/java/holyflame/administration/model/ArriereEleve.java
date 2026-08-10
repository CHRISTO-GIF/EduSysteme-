package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "arrieres_eleves")
public class ArriereEleve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eleve_id", nullable = false)
    private Eleve eleve;

    private String anneeScolaireOrigine;
    private Double montant;
    private Double montantRegle = 0.0;
    private String notes;
    private LocalDate dateEnregistrement;
    private Long etablissementId;

    public ArriereEleve() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
    public String getAnneeScolaireOrigine() { return anneeScolaireOrigine; }
    public void setAnneeScolaireOrigine(String anneeScolaireOrigine) { this.anneeScolaireOrigine = anneeScolaireOrigine; }
    public Double getMontant() { return montant; }
    public void setMontant(Double montant) { this.montant = montant; }
    public Double getMontantRegle() { return montantRegle; }
    public void setMontantRegle(Double montantRegle) { this.montantRegle = montantRegle; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDate getDateEnregistrement() { return dateEnregistrement; }
    public void setDateEnregistrement(LocalDate dateEnregistrement) { this.dateEnregistrement = dateEnregistrement; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
