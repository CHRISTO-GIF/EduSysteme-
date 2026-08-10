package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "fiches_controle")
public class FicheControle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classe_id")
    private Classe classe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enseignant_id")
    private Personnel enseignant;

    private String cours;
    private Integer effectifs;
    private LocalDate dateVisite;
    private String anneeScolaire;

    @Column(length = 4000)
    private String evaluationGlobale;

    /** INSUFFISANT, MOYEN, BIEN, TRES_BIEN — permet de suivre l'evolution d'un enseignant visite apres visite */
    private String noteGlobale;

    private Long coordonnateurId;
    private Long etablissementId;
    private LocalDateTime dateCreation;

    public FicheControle() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Classe getClasse() { return classe; }
    public void setClasse(Classe classe) { this.classe = classe; }
    public Personnel getEnseignant() { return enseignant; }
    public void setEnseignant(Personnel enseignant) { this.enseignant = enseignant; }
    public String getCours() { return cours; }
    public void setCours(String cours) { this.cours = cours; }
    public Integer getEffectifs() { return effectifs; }
    public void setEffectifs(Integer effectifs) { this.effectifs = effectifs; }
    public LocalDate getDateVisite() { return dateVisite; }
    public void setDateVisite(LocalDate dateVisite) { this.dateVisite = dateVisite; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public String getEvaluationGlobale() { return evaluationGlobale; }
    public void setEvaluationGlobale(String evaluationGlobale) { this.evaluationGlobale = evaluationGlobale; }
    public String getNoteGlobale() { return noteGlobale; }
    public void setNoteGlobale(String noteGlobale) { this.noteGlobale = noteGlobale; }
    public Long getCoordonnateurId() { return coordonnateurId; }
    public void setCoordonnateurId(Long coordonnateurId) { this.coordonnateurId = coordonnateurId; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
}
