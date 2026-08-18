package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "lignes_budget")
public class LigneBudget {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_comptable_id")
    private CategorieComptable categorieComptable; // REVENU/DEPENSE derive de categorieComptable.sens (PRODUIT/CHARGE)
    private Double montantPrevu;
    private Double montantReel;
    private Integer mois;       // 1-12, null = annuel
    private String anneeScolaire;
    private String notes;
    private LocalDate dateCreation;
    private Long etablissementId;

    public LigneBudget() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public CategorieComptable getCategorieComptable() { return categorieComptable; }
    public void setCategorieComptable(CategorieComptable categorieComptable) { this.categorieComptable = categorieComptable; }
    public boolean isRevenu() { return categorieComptable != null && "PRODUIT".equals(categorieComptable.getSens()); }
    public Double getMontantPrevu() { return montantPrevu; }
    public void setMontantPrevu(Double montantPrevu) { this.montantPrevu = montantPrevu; }
    public Double getMontantReel() { return montantReel; }
    public void setMontantReel(Double montantReel) { this.montantReel = montantReel; }
    public Integer getMois() { return mois; }
    public void setMois(Integer mois) { this.mois = mois; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDate getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
