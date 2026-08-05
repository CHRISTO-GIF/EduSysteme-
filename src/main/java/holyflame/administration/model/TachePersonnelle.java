package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Tache personnelle qu'un eleve cree lui-meme dans son Kanban de devoirs, hors devoirs assignes par un enseignant. */
@Entity
@Table(name = "taches_personnelles")
public class TachePersonnelle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long eleveId;

    @Column(nullable = false)
    private String titre;

    private LocalDate dateEcheance;

    private String statut = "A_FAIRE"; // A_FAIRE, EN_COURS, TERMINE
    private Integer pourcentageAvancement = 0;

    private LocalDateTime dateCreation = LocalDateTime.now();

    public TachePersonnelle() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEleveId() { return eleveId; }
    public void setEleveId(Long eleveId) { this.eleveId = eleveId; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public LocalDate getDateEcheance() { return dateEcheance; }
    public void setDateEcheance(LocalDate dateEcheance) { this.dateEcheance = dateEcheance; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Integer getPourcentageAvancement() { return pourcentageAvancement; }
    public void setPourcentageAvancement(Integer pourcentageAvancement) { this.pourcentageAvancement = pourcentageAvancement; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }
}
