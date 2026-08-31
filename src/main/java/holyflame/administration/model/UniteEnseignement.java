package holyflame.administration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "unites_enseignement",
       uniqueConstraints = @UniqueConstraint(columnNames = {"code", "etablissement_id"}))
public class UniteEnseignement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String intitule;

    // Crédits capitalisables (ECTS ou équivalent LMD) portés par l'UE,
    // indépendamment du système de notation affiché (paramètre NOTATION_SYSTEME)
    @Column(nullable = false)
    private Integer credits;

    // Semestre de la filière où l'UE est enseignée (S1, S2, ...)
    @Column(nullable = false)
    private Integer semestre;

    @Column(name = "filiere_id", nullable = false)
    private Long filiereId;

    @Column(name = "etablissement_id")
    private Long etablissementId;

    public UniteEnseignement() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getIntitule() { return intitule; }
    public void setIntitule(String intitule) { this.intitule = intitule; }
    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }
    public Integer getSemestre() { return semestre; }
    public void setSemestre(Integer semestre) { this.semestre = semestre; }
    public Long getFiliereId() { return filiereId; }
    public void setFiliereId(Long filiereId) { this.filiereId = filiereId; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
