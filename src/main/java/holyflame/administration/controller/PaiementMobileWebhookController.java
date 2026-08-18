package holyflame.administration.controller;

import holyflame.administration.model.Eleve;
import holyflame.administration.model.Paiement;
import holyflame.administration.model.TransactionMobile;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.FraisScolariteRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.TransactionMobileRepository;
import holyflame.administration.service.CinetPayService;
import holyflame.administration.service.JournalService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Webhook public (voir SecurityConfig, /paiements/mobile/notification est en permitAll) appele
 * par CinetPay pour signaler qu'une transaction a change de statut. Le corps de la notification
 * ne contient JAMAIS le statut lui-meme — c'est une regle de securite imposee par CinetPay pour
 * empecher qu'un tiers rejouant/forgeant l'appel HTTP fasse valider un faux paiement. On rappelle
 * donc systematiquement l'API de verification pour obtenir le vrai statut avant d'enregistrer
 * quoi que ce soit. L'endpoint est idempotent : CinetPay peut l'appeler plusieurs fois pour la
 * meme transaction sans qu'un doublon de Paiement soit cree.
 */
@Controller
@RequestMapping("/paiements/mobile/notification")
public class PaiementMobileWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaiementMobileWebhookController.class);

    @Autowired private TransactionMobileRepository transactionMobileRepository;
    @Autowired private CinetPayService cinetPayService;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private FraisScolariteRepository fraisScolariteRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private JournalService journalService;

    @PostMapping
    public ResponseEntity<String> notifierPost(HttpServletRequest request) {
        return traiter(request);
    }

    @GetMapping
    public ResponseEntity<String> notifierGet(HttpServletRequest request) {
        return traiter(request);
    }

    private ResponseEntity<String> traiter(HttpServletRequest request) {
        String transactionId = premierNonVide(request.getParameter("cpm_trans_id"), request.getParameter("transaction_id"));
        if (transactionId == null) {
            return ResponseEntity.ok("OK");
        }

        TransactionMobile tx = transactionMobileRepository.findByTransactionId(transactionId).orElse(null);
        if (tx == null) {
            log.warn("Notification CinetPay pour une transaction inconnue : {}", transactionId);
            return ResponseEntity.ok("OK");
        }
        if ("ACCEPTEE".equals(tx.getStatut())) {
            return ResponseEntity.ok("OK"); // deja traitee — idempotence
        }

        CinetPayService.ResultatVerification resultat = cinetPayService.verifierTransaction(transactionId);
        if (resultat == null) {
            log.error("Verification CinetPay impossible pour la transaction {}", transactionId);
            return ResponseEntity.ok("OK");
        }
        if (!resultat.accepte) {
            tx.setStatut("REFUSEE");
            transactionMobileRepository.save(tx);
            return ResponseEntity.ok("OK");
        }

        Eleve eleve = eleveRepository.findById(tx.getEleveId()).orElse(null);
        if (eleve == null) {
            log.error("Transaction {} confirmee mais eleve {} introuvable.", transactionId, tx.getEleveId());
            return ResponseEntity.ok("OK");
        }

        Paiement p = new Paiement();
        p.setEleve(eleve);
        p.setMontantVerse(resultat.montant > 0 ? resultat.montant : tx.getMontant());
        p.setDatePaiement(LocalDateTime.now());
        p.setTypePaiement("SCOLARITE");
        p.setModePaiement("MOBILE_MONEY");
        p.setDescription(tx.getDescription() + (resultat.operateur != null ? " (" + resultat.operateur + ")" : ""));
        p.setRecuNumero(prochainNumeroRecuMobile(tx.getEtablissementId()));
        if (tx.getFraisScolariteId() != null) {
            fraisScolariteRepository.findById(tx.getFraisScolariteId()).ifPresent(p::setFraisScolarite);
        }
        if (eleve.getClasse() != null) p.setAnneeScolaire(eleve.getClasse().getAnneeScolaire());
        paiementRepository.save(p);

        tx.setStatut("ACCEPTEE");
        tx.setOperateur(resultat.operateur);
        tx.setDateConfirmation(LocalDateTime.now());
        tx.setPaiementId(p.getId());
        transactionMobileRepository.save(tx);

        journalService.log("PAIEMENT_MOBILE_CONFIRME", "FINANCES",
            eleve.getNom() + " " + eleve.getPrenom() + " — " + p.getMontantVerse() + " F via mobile money"
                + (resultat.operateur != null ? " (" + resultat.operateur + ")" : ""));

        return ResponseEntity.ok("OK");
    }

    private String premierNonVide(String... valeurs) {
        for (String v : valeurs) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private String prochainNumeroRecuMobile(Long etabId) {
        long compte = paiementRepository.findByEtablissementId(etabId).stream()
            .filter(p -> "MOBILE_MONEY".equals(p.getModePaiement()))
            .count();
        return "MM-" + LocalDate.now().getYear() + "-" + String.format("%04d", compte + 1);
    }
}
