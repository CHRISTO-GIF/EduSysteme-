package holyflame.administration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "facultes",
       uniqueConstraints = @UniqueConstraint(columnNames = {"code", "etablissement_id"}))
public class Faculte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String code;

    @Column(name = "etablissement_id")
    private Long etablissementId;

    public Faculte() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
