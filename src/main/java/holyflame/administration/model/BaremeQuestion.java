package holyflame.administration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "bareme_questions")
public class BaremeQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examen_id", nullable = false)
    private Examen examen;

    private Integer ordre;
    private String titre;      // ex: "Question 1"
    private String sousTitre;  // ex: "Theorie & Concepts"
    private Double bareme;     // points maximum

    public BaremeQuestion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Examen getExamen() { return examen; }
    public void setExamen(Examen examen) { this.examen = examen; }
    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getSousTitre() { return sousTitre; }
    public void setSousTitre(String sousTitre) { this.sousTitre = sousTitre; }
    public Double getBareme() { return bareme; }
    public void setBareme(Double bareme) { this.bareme = bareme; }
}
