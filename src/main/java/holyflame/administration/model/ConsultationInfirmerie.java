package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Passage d'un eleve a l'infirmerie : motif, soins prodigues, orientation finale.
 * Une consultation dont l'orientation est REPOS_INFIRMERIE ou HOPITAL_SAMU reste EN_COURS
 * (eleve encore pris en charge) jusqu'a ce que l'infirmier la cloture explicitement -
 * c'est ce qui alimente "En observation" et "Alertes actives" sur le tableau de bord.
 */
@Entity
@Table(name = "consultations_infirmerie")
public class ConsultationInfirmerie {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eleve_id", nullable = false)
    private Eleve eleve;

    @Column(nullable = false)
    private LocalDateTime dateHeure;

    private String motif;

    @Column(length = 2000)
    private String observations;

    private boolean parentsInformes;

    /** RETOUR_CLASSE, REPOS_INFIRMERIE, RENVOYE_DOMICILE, HOPITAL_SAMU */
    private String orientation;

    /** EN_COURS, TERMINEE */
    @Column(nullable = false)
    private String statut = "TERMINEE";

    private Long infirmierParId;
    private String anneeScolaire;
    private Long etablissementId;

    public ConsultationInfirmerie() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
    public LocalDateTime getDateHeure() { return dateHeure; }
    public void setDateHeure(LocalDateTime dateHeure) { this.dateHeure = dateHeure; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }
    public boolean isParentsInformes() { return parentsInformes; }
    public void setParentsInformes(boolean parentsInformes) { this.parentsInformes = parentsInformes; }
    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Long getInfirmierParId() { return infirmierParId; }
    public void setInfirmierParId(Long infirmierParId) { this.infirmierParId = infirmierParId; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
