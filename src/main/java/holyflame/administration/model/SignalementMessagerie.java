package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signalements_messagerie")
public class SignalementMessagerie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String signalePar;

    @Column(nullable = false)
    private String concernant;

    private String motif;

    @Column(length = 2000)
    private String description;

    private LocalDateTime dateSignalement;

    /** OUVERT ou TRAITE */
    private String statut = "OUVERT";

    private Long etablissementId;

    public SignalementMessagerie() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSignalePar() { return signalePar; }
    public void setSignalePar(String signalePar) { this.signalePar = signalePar; }
    public String getConcernant() { return concernant; }
    public void setConcernant(String concernant) { this.concernant = concernant; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getDateSignalement() { return dateSignalement; }
    public void setDateSignalement(LocalDateTime dateSignalement) { this.dateSignalement = dateSignalement; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
