package holyflame.administration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "notes_questions")
public class NoteQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "examen_id", nullable = false)
    private Examen examen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eleve_id", nullable = false)
    private Eleve eleve;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bareme_question_id", nullable = false)
    private BaremeQuestion baremeQuestion;

    private Double points;

    public NoteQuestion() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Examen getExamen() { return examen; }
    public void setExamen(Examen examen) { this.examen = examen; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
    public BaremeQuestion getBaremeQuestion() { return baremeQuestion; }
    public void setBaremeQuestion(BaremeQuestion baremeQuestion) { this.baremeQuestion = baremeQuestion; }
    public Double getPoints() { return points; }
    public void setPoints(Double points) { this.points = points; }
}
