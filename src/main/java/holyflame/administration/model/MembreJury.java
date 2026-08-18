package holyflame.administration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "membres_jury")
public class MembreJury {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seance_deliberation_id", nullable = false)
    private SeanceDeliberation seanceDeliberation;

    private String nomComplet;
    private String role; // President du Jury, Professeur Principal, Professeur, CPE, Delegue(e) Parents, ...
    private String statutPresence = "PRESENT"; // PRESENT, ABSENT, ABSENT_EXCUSE

    public MembreJury() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SeanceDeliberation getSeanceDeliberation() { return seanceDeliberation; }
    public void setSeanceDeliberation(SeanceDeliberation seanceDeliberation) { this.seanceDeliberation = seanceDeliberation; }
    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatutPresence() { return statutPresence; }
    public void setStatutPresence(String statutPresence) { this.statutPresence = statutPresence; }
}
