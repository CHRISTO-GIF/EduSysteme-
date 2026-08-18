package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "salaires_mensuels")
public class SalaireMensuel {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personnel_id", nullable = false)
    private Personnel personnel;

    private int mois;
    private int annee;
    private String anneeScolaire;
    private LocalDate periodeDebut;
    private LocalDate periodeFin;

    private Double totalBrut;
    private Double totalRetenuesSalariales;
    private Double totalChargesPatronales;
    private Double netAPayer;

    private String statut = "EN_ATTENTE"; // EN_ATTENTE, PAYE
    private LocalDate datePaiement;

    @OneToMany(mappedBy = "salaireMensuel", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<LigneSalaire> lignes = new ArrayList<>();

    public SalaireMensuel() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Personnel getPersonnel() { return personnel; }
    public void setPersonnel(Personnel personnel) { this.personnel = personnel; }
    public int getMois() { return mois; }
    public void setMois(int mois) { this.mois = mois; }
    public int getAnnee() { return annee; }
    public void setAnnee(int annee) { this.annee = annee; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public LocalDate getPeriodeDebut() { return periodeDebut; }
    public void setPeriodeDebut(LocalDate periodeDebut) { this.periodeDebut = periodeDebut; }
    public LocalDate getPeriodeFin() { return periodeFin; }
    public void setPeriodeFin(LocalDate periodeFin) { this.periodeFin = periodeFin; }
    public Double getTotalBrut() { return totalBrut; }
    public void setTotalBrut(Double totalBrut) { this.totalBrut = totalBrut; }
    public Double getTotalRetenuesSalariales() { return totalRetenuesSalariales; }
    public void setTotalRetenuesSalariales(Double totalRetenuesSalariales) { this.totalRetenuesSalariales = totalRetenuesSalariales; }
    public Double getTotalChargesPatronales() { return totalChargesPatronales; }
    public void setTotalChargesPatronales(Double totalChargesPatronales) { this.totalChargesPatronales = totalChargesPatronales; }
    public Double getNetAPayer() { return netAPayer; }
    public void setNetAPayer(Double netAPayer) { this.netAPayer = netAPayer; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public LocalDate getDatePaiement() { return datePaiement; }
    public void setDatePaiement(LocalDate datePaiement) { this.datePaiement = datePaiement; }
    public List<LigneSalaire> getLignes() { return lignes; }
    public void setLignes(List<LigneSalaire> lignes) { this.lignes = lignes; }
}
