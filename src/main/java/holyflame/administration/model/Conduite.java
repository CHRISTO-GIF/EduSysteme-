package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conduites")
public class Conduite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eleve_id", nullable = false)
    private Eleve eleve;

    private Integer trimestre;
    private String anneeScolaire;

    /** EXCELLENTE, BONNE, MOYENNE, A_AMELIORER */
    private String evaluation;

    @Column(length = 1000)
    private String commentaire;

    private LocalDateTime saisieAt;
    private Long saisieParId;

    public Conduite() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
    public Integer getTrimestre() { return trimestre; }
    public void setTrimestre(Integer trimestre) { this.trimestre = trimestre; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public String getEvaluation() { return evaluation; }
    public void setEvaluation(String evaluation) { this.evaluation = evaluation; }
    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
    public LocalDateTime getSaisieAt() { return saisieAt; }
    public void setSaisieAt(LocalDateTime saisieAt) { this.saisieAt = saisieAt; }
    public Long getSaisieParId() { return saisieParId; }
    public void setSaisieParId(Long saisieParId) { this.saisieParId = saisieParId; }
}
