package holyflame.administration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "categories_comptables")
public class CategorieComptable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    private String sens = "CHARGE"; // CHARGE, PRODUIT
    private String groupe; // FOURNITURES_SCOLAIRES, ENTRETIEN_SALUBRITE, FONCTIONNEMENT, INVESTISSEMENT (rapport econome)
    private boolean actif = true;
    private Long etablissementId;

    public CategorieComptable() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getSens() { return sens; }
    public void setSens(String sens) { this.sens = sens; }
    public String getGroupe() { return groupe; }
    public void setGroupe(String groupe) { this.groupe = groupe; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
