package holyflame.administration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "departements",
       uniqueConstraints = @UniqueConstraint(columnNames = {"code", "etablissement_id"}))
public class Departement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String code;

    @Column(name = "faculte_id", nullable = false)
    private Long faculteId;

    @Column(name = "etablissement_id")
    private Long etablissementId;

    public Departement() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getFaculteId() { return faculteId; }
    public void setFaculteId(Long faculteId) { this.faculteId = faculteId; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
