package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages_prives")
public class MessagePrive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String expediteurEmail;

    @Column(nullable = false)
    private String destinataireEmail;

    @Column(length = 2000, nullable = false)
    private String contenu;

    private LocalDateTime dateEnvoi;
    private boolean lu;
    private Long etablissementId;

    private String pieceJointeNom;
    private String pieceJointeChemin;
    private String pieceJointeType;
    private Long pieceJointeTaille;

    public MessagePrive() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExpediteurEmail() { return expediteurEmail; }
    public void setExpediteurEmail(String expediteurEmail) { this.expediteurEmail = expediteurEmail; }
    public String getDestinataireEmail() { return destinataireEmail; }
    public void setDestinataireEmail(String destinataireEmail) { this.destinataireEmail = destinataireEmail; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public LocalDateTime getDateEnvoi() { return dateEnvoi; }
    public void setDateEnvoi(LocalDateTime dateEnvoi) { this.dateEnvoi = dateEnvoi; }
    public boolean isLu() { return lu; }
    public void setLu(boolean lu) { this.lu = lu; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
    public String getPieceJointeNom() { return pieceJointeNom; }
    public void setPieceJointeNom(String pieceJointeNom) { this.pieceJointeNom = pieceJointeNom; }
    public String getPieceJointeChemin() { return pieceJointeChemin; }
    public void setPieceJointeChemin(String pieceJointeChemin) { this.pieceJointeChemin = pieceJointeChemin; }
    public String getPieceJointeType() { return pieceJointeType; }
    public void setPieceJointeType(String pieceJointeType) { this.pieceJointeType = pieceJointeType; }
    public Long getPieceJointeTaille() { return pieceJointeTaille; }
    public void setPieceJointeTaille(Long pieceJointeTaille) { this.pieceJointeTaille = pieceJointeTaille; }
}
