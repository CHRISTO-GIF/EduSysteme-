package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "avis_parents")
public class AvisParent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eleve_id", nullable = false)
    private Eleve eleve;

    /** RETARDS, DISCIPLINE, PEDAGOGIE, COMMUNICATION, SECURITE, AUTRE */
    private String categorie;

    @Column(length = 2000)
    private String description;

    private String parentEmail;
    private LocalDateTime dateSignalement;

    /** OUVERT, TRAITE */
    private String statut = "OUVERT";

    private Long etablissementId;

    public AvisParent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getParentEmail() { return parentEmail; }
    public void setParentEmail(String parentEmail) { this.parentEmail = parentEmail; }
    public LocalDateTime getDateSignalement() { return dateSignalement; }
    public void setDateSignalement(LocalDateTime dateSignalement) { this.dateSignalement = dateSignalement; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
