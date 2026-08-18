package holyflame.administration.model;

import jakarta.persistence.*;

/**
 * Condition medicale connue d'un eleve (PAI, allergie...) consultee par l'infirmerie
 * avant/pendant une prise en charge. Desactivee plutot que supprimee pour garder l'historique.
 */
@Entity
@Table(name = "conditions_medicales")
public class ConditionMedicale {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eleve_id", nullable = false)
    private Eleve eleve;

    /** ALLERGIE, ASTHME, DIABETE, EPILEPSIE, AUTRE */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String libelle;

    @Column(length = 2000)
    private String protocole;

    @Column(nullable = false)
    private boolean actif = true;

    private Long etablissementId;

    public ConditionMedicale() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public String getProtocole() { return protocole; }
    public void setProtocole(String protocole) { this.protocole = protocole; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
