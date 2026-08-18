package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Cycle de vie d'une annee scolaire pour un etablissement : preparation, activation,
 * cloture. Sert de garde-fou pour empecher toute modification sur une annee cloturee
 * et de point d'entree pour dupliquer la structure (classes, budget) vers une nouvelle annee.
 */
@Entity
@Table(name = "annees_scolaires")
public class AnneeScolaire {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String libelle;

    /** ACTIVE (une seule a la fois par etablissement), INACTIVE, CLOTUREE */
    @Column(nullable = false)
    private String statut = "INACTIVE";

    private String dupliqueeDepuis;
    private LocalDate dateCreation;
    private LocalDate dateActivation;
    private LocalDate dateCloture;
    private Long clotureParId;
    private Long etablissementId;

    public AnneeScolaire() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getDupliqueeDepuis() { return dupliqueeDepuis; }
    public void setDupliqueeDepuis(String dupliqueeDepuis) { this.dupliqueeDepuis = dupliqueeDepuis; }
    public LocalDate getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }
    public LocalDate getDateActivation() { return dateActivation; }
    public void setDateActivation(LocalDate dateActivation) { this.dateActivation = dateActivation; }
    public LocalDate getDateCloture() { return dateCloture; }
    public void setDateCloture(LocalDate dateCloture) { this.dateCloture = dateCloture; }
    public Long getClotureParId() { return clotureParId; }
    public void setClotureParId(Long clotureParId) { this.clotureParId = clotureParId; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
