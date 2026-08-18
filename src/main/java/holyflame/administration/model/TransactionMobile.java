package holyflame.administration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Suivi d'une tentative de paiement par mobile money (CinetPay) entre son initiation et sa
 * confirmation. Un Paiement reel n'est cree dans la table paiements qu'une fois le statut
 * verifie aupres de CinetPay comme ACCEPTEE — jamais depuis le seul appel webhook, voir
 * CinetPayService.
 */
@Entity
@Table(name = "transactions_mobile")
public class TransactionMobile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String transactionId;

    private Long etablissementId;
    private Long eleveId;
    private Long fraisScolariteId;
    private Double montant;
    private String description;
    private String statut; // EN_ATTENTE, ACCEPTEE, REFUSEE
    private String operateur;
    private LocalDateTime dateCreation;
    private LocalDateTime dateConfirmation;
    private Long paiementId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    public Long getEleveId() { return eleveId; }
    public void setEleveId(Long eleveId) { this.eleveId = eleveId; }

    public Long getFraisScolariteId() { return fraisScolariteId; }
    public void setFraisScolariteId(Long fraisScolariteId) { this.fraisScolariteId = fraisScolariteId; }

    public Double getMontant() { return montant; }
    public void setMontant(Double montant) { this.montant = montant; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public String getOperateur() { return operateur; }
    public void setOperateur(String operateur) { this.operateur = operateur; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public LocalDateTime getDateConfirmation() { return dateConfirmation; }
    public void setDateConfirmation(LocalDateTime dateConfirmation) { this.dateConfirmation = dateConfirmation; }

    public Long getPaiementId() { return paiementId; }
    public void setPaiementId(Long paiementId) { this.paiementId = paiementId; }
}
