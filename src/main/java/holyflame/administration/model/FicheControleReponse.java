package holyflame.administration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "fiche_controle_reponses")
public class FicheControleReponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fiche_controle_id", nullable = false)
    private FicheControle ficheControle;

    private String section;
    private String critere;

    @Column(length = 1000)
    private String appreciation;

    @Column(length = 1000)
    private String conseils;

    public FicheControleReponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public FicheControle getFicheControle() { return ficheControle; }
    public void setFicheControle(FicheControle ficheControle) { this.ficheControle = ficheControle; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getCritere() { return critere; }
    public void setCritere(String critere) { this.critere = critere; }
    public String getAppreciation() { return appreciation; }
    public void setAppreciation(String appreciation) { this.appreciation = appreciation; }
    public String getConseils() { return conseils; }
    public void setConseils(String conseils) { this.conseils = conseils; }
}
