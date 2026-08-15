package holyflame.administration.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// Le matricule n'est unique QUE dans son etablissement (colonne composite), pas sur toute la
// base : deux ecoles differentes doivent pouvoir utiliser le meme numero sans se gener.
@Entity
@Table(name = "eleves", uniqueConstraints = @UniqueConstraint(columnNames = {"matricule", "etablissement_id"}))
public class Eleve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String matricule;

    private String nom;
    private String prenom;
    private LocalDate dateNaissance;
    private String genre; // MASCULIN, FEMININ, AUTRE
    private String nationalite;
    private String telephoneParent;
    private String emailParent;
    private String adresse;
    private String statutInscription;
    private String compteEmail;
    private Long etablissementId;

    private String nomTuteur;
    private String lienParente; // PERE, MERE, TUTEUR_LEGAL, AUTRE
    private String contactUrgenceNom;
    private String contactUrgenceTelephone;
    private String urgenceRelation;

    private String pereNom;
    private String pereTelephone;
    private String pereProfession;
    private String pereEmail;
    private String pereCodeAcces;

    private String mereNom;
    private String mereTelephone;
    private String mereProfession;
    private String mereEmail;
    private String mereCodeAcces;
    private LocalDate dateInscription;
    private String ecoleProvenance;
    private String langueVivante1;
    private String langueVivante2;

    @Column(length = 500)
    private String optionsSpecialites; // liste separee par des virgules

    private LocalDateTime dernierRappelPaiement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "classe_id", nullable = false)
    private Classe classe;

    public Eleve() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMatricule() {
        return matricule;
    }

    public void setMatricule(String matricule) {
        this.matricule = matricule;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getPrenom() {
        return prenom;
    }

    public void setPrenom(String prenom) {
        this.prenom = prenom;
    }

    public LocalDate getDateNaissance() {
        return dateNaissance;
    }

    public void setDateNaissance(LocalDate dateNaissance) {
        this.dateNaissance = dateNaissance;
    }

    public String getTelephoneParent() {
        return telephoneParent;
    }

    public void setTelephoneParent(String telephoneParent) {
        this.telephoneParent = telephoneParent;
    }

    public String getEmailParent() {
        return emailParent;
    }

    public void setEmailParent(String emailParent) {
        this.emailParent = emailParent;
    }

    public String getAdresse() {
        return adresse;
    }

    public void setAdresse(String adresse) {
        this.adresse = adresse;
    }

    public String getStatutInscription() {
        return statutInscription;
    }

    public void setStatutInscription(String statutInscription) {
        this.statutInscription = statutInscription;
    }

    public String getCompteEmail() { return compteEmail; }
    public void setCompteEmail(String compteEmail) { this.compteEmail = compteEmail; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    public Classe getClasse() {
        return classe;
    }

    public void setClasse(Classe classe) {
        this.classe = classe;
    }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getNationalite() { return nationalite; }
    public void setNationalite(String nationalite) { this.nationalite = nationalite; }
    public String getNomTuteur() { return nomTuteur; }
    public void setNomTuteur(String nomTuteur) { this.nomTuteur = nomTuteur; }
    public String getLienParente() { return lienParente; }
    public void setLienParente(String lienParente) { this.lienParente = lienParente; }
    public String getContactUrgenceNom() { return contactUrgenceNom; }
    public void setContactUrgenceNom(String contactUrgenceNom) { this.contactUrgenceNom = contactUrgenceNom; }
    public String getContactUrgenceTelephone() { return contactUrgenceTelephone; }
    public void setContactUrgenceTelephone(String contactUrgenceTelephone) { this.contactUrgenceTelephone = contactUrgenceTelephone; }
    public LocalDate getDateInscription() { return dateInscription; }
    public void setDateInscription(LocalDate dateInscription) { this.dateInscription = dateInscription; }
    public String getEcoleProvenance() { return ecoleProvenance; }
    public void setEcoleProvenance(String ecoleProvenance) { this.ecoleProvenance = ecoleProvenance; }
    public String getLangueVivante1() { return langueVivante1; }
    public void setLangueVivante1(String langueVivante1) { this.langueVivante1 = langueVivante1; }
    public String getLangueVivante2() { return langueVivante2; }
    public void setLangueVivante2(String langueVivante2) { this.langueVivante2 = langueVivante2; }
    public String getOptionsSpecialites() { return optionsSpecialites; }
    public void setOptionsSpecialites(String optionsSpecialites) { this.optionsSpecialites = optionsSpecialites; }
    public LocalDateTime getDernierRappelPaiement() { return dernierRappelPaiement; }
    public void setDernierRappelPaiement(LocalDateTime dernierRappelPaiement) { this.dernierRappelPaiement = dernierRappelPaiement; }
    public String getUrgenceRelation() { return urgenceRelation; }
    public void setUrgenceRelation(String urgenceRelation) { this.urgenceRelation = urgenceRelation; }

    public String getPereNom() { return pereNom; }
    public void setPereNom(String pereNom) { this.pereNom = pereNom; }
    public String getPereTelephone() { return pereTelephone; }
    public void setPereTelephone(String pereTelephone) { this.pereTelephone = pereTelephone; }
    public String getPereProfession() { return pereProfession; }
    public void setPereProfession(String pereProfession) { this.pereProfession = pereProfession; }
    public String getPereEmail() { return pereEmail; }
    public void setPereEmail(String pereEmail) { this.pereEmail = pereEmail; }
    public String getPereCodeAcces() { return pereCodeAcces; }
    public void setPereCodeAcces(String pereCodeAcces) { this.pereCodeAcces = pereCodeAcces; }

    public String getMereNom() { return mereNom; }
    public void setMereNom(String mereNom) { this.mereNom = mereNom; }
    public String getMereTelephone() { return mereTelephone; }
    public void setMereTelephone(String mereTelephone) { this.mereTelephone = mereTelephone; }
    public String getMereProfession() { return mereProfession; }
    public void setMereProfession(String mereProfession) { this.mereProfession = mereProfession; }
    public String getMereEmail() { return mereEmail; }
    public void setMereEmail(String mereEmail) { this.mereEmail = mereEmail; }
    public String getMereCodeAcces() { return mereCodeAcces; }
    public void setMereCodeAcces(String mereCodeAcces) { this.mereCodeAcces = mereCodeAcces; }
}
