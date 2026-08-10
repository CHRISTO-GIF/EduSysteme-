package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "depenses")
public class Depense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String designation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categorie_comptable_id")
    private CategorieComptable categorieComptable;

    private String sens = "CHARGE"; // CHARGE, PRODUIT (recette diverse : don, subvention, pret...)
    private String beneficiaire; // nom du fournisseur / prestataire
    private Double quantite;
    private Double prixUnitaire;
    private Double montant;
    private LocalDate dateDepense;
    private String statut = "PAYE"; // PAYE, EN_ATTENTE
    private String anneeScolaire;
    private Long etablissementId;

    private String justificatifPath;
    private String justificatifNomOriginal;

    public Depense() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public CategorieComptable getCategorieComptable() { return categorieComptable; }
    public void setCategorieComptable(CategorieComptable categorieComptable) { this.categorieComptable = categorieComptable; }
    public String getSens() { return sens; }
    public void setSens(String sens) { this.sens = sens; }
    public String getBeneficiaire() { return beneficiaire; }
    public void setBeneficiaire(String beneficiaire) { this.beneficiaire = beneficiaire; }
    public Double getQuantite() { return quantite; }
    public void setQuantite(Double quantite) { this.quantite = quantite; }
    public Double getPrixUnitaire() { return prixUnitaire; }
    public void setPrixUnitaire(Double prixUnitaire) { this.prixUnitaire = prixUnitaire; }
    public Double getMontant() { return montant; }
    public void setMontant(Double montant) { this.montant = montant; }
    public LocalDate getDateDepense() { return dateDepense; }
    public void setDateDepense(LocalDate dateDepense) { this.dateDepense = dateDepense; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
    public String getJustificatifPath() { return justificatifPath; }
    public void setJustificatifPath(String justificatifPath) { this.justificatifPath = justificatifPath; }
    public String getJustificatifNomOriginal() { return justificatifNomOriginal; }
    public void setJustificatifNomOriginal(String justificatifNomOriginal) { this.justificatifNomOriginal = justificatifNomOriginal; }
}
