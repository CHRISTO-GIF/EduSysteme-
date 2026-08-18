package holyflame.administration.model;

import jakarta.persistence.*;

@Entity
@Table(name = "lignes_salaire")
public class LigneSalaire {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salaire_mensuel_id", nullable = false)
    private SalaireMensuel salaireMensuel;

    private String section; // GAIN, RETENUE_SALARIALE, CHARGE_PATRONALE
    private String libelle;
    private Double base;
    private Double taux;
    private Double montant;
    private int ordre;

    public LigneSalaire() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public SalaireMensuel getSalaireMensuel() { return salaireMensuel; }
    public void setSalaireMensuel(SalaireMensuel salaireMensuel) { this.salaireMensuel = salaireMensuel; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public String getLibelle() { return libelle; }
    public void setLibelle(String libelle) { this.libelle = libelle; }
    public Double getBase() { return base; }
    public void setBase(Double base) { this.base = base; }
    public Double getTaux() { return taux; }
    public void setTaux(Double taux) { this.taux = taux; }
    public Double getMontant() { return montant; }
    public void setMontant(Double montant) { this.montant = montant; }
    public int getOrdre() { return ordre; }
    public void setOrdre(int ordre) { this.ordre = ordre; }
}
