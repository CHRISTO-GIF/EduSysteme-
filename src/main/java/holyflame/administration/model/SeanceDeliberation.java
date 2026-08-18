package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Une seance de conseil de classe / deliberation : soit un conseil trimestriel ordinaire
 * (trimestre renseigne, aucune decision de passage rattachee), soit le conseil de fin
 * d'annee qui statue sur le passage (trimestre = null).
 */
@Entity
@Table(name = "seances_deliberation")
public class SeanceDeliberation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classe_id", nullable = false)
    private Classe classe;

    private String anneeScolaire;
    private Integer trimestre; // 1, 2, 3, ou null = deliberation de fin d'annee
    private LocalDate dateSeance;
    private String rapporteurNom;
    private String statut = "EN_PREPARATION"; // EN_PREPARATION, CLOTUREE
    private String referencePv;
    private Long etablissementId;

    public SeanceDeliberation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Classe getClasse() { return classe; }
    public void setClasse(Classe classe) { this.classe = classe; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public Integer getTrimestre() { return trimestre; }
    public void setTrimestre(Integer trimestre) { this.trimestre = trimestre; }
    public LocalDate getDateSeance() { return dateSeance; }
    public void setDateSeance(LocalDate dateSeance) { this.dateSeance = dateSeance; }
    public String getRapporteurNom() { return rapporteurNom; }
    public void setRapporteurNom(String rapporteurNom) { this.rapporteurNom = rapporteurNom; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getReferencePv() { return referencePv; }
    public void setReferencePv(String referencePv) { this.referencePv = referencePv; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
