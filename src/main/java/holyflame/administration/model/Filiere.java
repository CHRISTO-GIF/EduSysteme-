package holyflame.administration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "filieres",
       uniqueConstraints = @UniqueConstraint(columnNames = {"code", "etablissement_id"}))
public class Filiere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String code;

    // Cycle LMD : LICENCE, MASTER ou DOCTORAT
    private String niveau;

    @Column(name = "faculte_id", nullable = false)
    private Long faculteId;

    // Facultatif : certains établissements sautent le niveau département
    // (paramètre STRUCTURE_NIVEAUX, voir ParametreRepository)
    @Column(name = "departement_id")
    private Long departementId;

    @Column(name = "etablissement_id")
    private Long etablissementId;

    public Filiere() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getNiveau() { return niveau; }
    public void setNiveau(String niveau) { this.niveau = niveau; }
    public Long getFaculteId() { return faculteId; }
    public void setFaculteId(Long faculteId) { this.faculteId = faculteId; }
    public Long getDepartementId() { return departementId; }
    public void setDepartementId(Long departementId) { this.departementId = departementId; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
