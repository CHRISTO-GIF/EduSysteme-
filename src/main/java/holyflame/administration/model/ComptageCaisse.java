package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "comptages_caisse")
public class ComptageCaisse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate dateComptage;
    private Double soldeTheorique;
    private Double soldeCompte;
    private Double ecart;
    private String note;
    private Long enregistreParId;
    private Long etablissementId;

    public ComptageCaisse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getDateComptage() { return dateComptage; }
    public void setDateComptage(LocalDate dateComptage) { this.dateComptage = dateComptage; }
    public Double getSoldeTheorique() { return soldeTheorique; }
    public void setSoldeTheorique(Double soldeTheorique) { this.soldeTheorique = soldeTheorique; }
    public Double getSoldeCompte() { return soldeCompte; }
    public void setSoldeCompte(Double soldeCompte) { this.soldeCompte = soldeCompte; }
    public Double getEcart() { return ecart; }
    public void setEcart(Double ecart) { this.ecart = ecart; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Long getEnregistreParId() { return enregistreParId; }
    public void setEnregistreParId(Long enregistreParId) { this.enregistreParId = enregistreParId; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
}
