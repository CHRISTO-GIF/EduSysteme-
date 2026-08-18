package holyflame.administration.model;

import jakarta.persistence.*;

/**
 * Article de pharmacie/materiel de premiers secours suivi par l'infirmerie.
 * quantiteMax sert de reference pour la barre de niveau de stock (pourcentage).
 */
@Entity
@Table(name = "articles_infirmerie")
public class ArticleInfirmerie {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String designation;

    @Column(nullable = false)
    private Integer quantiteActuelle;

    @Column(nullable = false)
    private Integer quantiteMax;

    private String unite;
    private Long etablissementId;

    public ArticleInfirmerie() {}

    public int getPourcentage() {
        if (quantiteMax == null || quantiteMax == 0 || quantiteActuelle == null) return 0;
        return Math.max(0, Math.min(100, Math.round(quantiteActuelle * 100.0f / quantiteMax)));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public Integer getQuantiteActuelle() { return quantiteActuelle; }
    public void setQuantiteActuelle(Integer quantiteActuelle) { this.quantiteActuelle = quantiteActuelle; }
    public Integer getQuantiteMax() { return quantiteMax; }
    public void setQuantiteMax(Integer quantiteMax) { this.quantiteMax = quantiteMax; }
    public String getUnite() { return unite; }
    public void setUnite(String unite) { this.unite = unite; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
