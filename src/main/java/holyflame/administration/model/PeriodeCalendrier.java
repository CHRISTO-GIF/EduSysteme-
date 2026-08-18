package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Periode non enseignee du calendrier scolaire (vacances, jour ferie...), utilisee pour
 * calculer un nombre de jours d'ecole reel (hors weekends et hors ces periodes) plutot
 * que de compter naivement tous les jours calendaires entre deux dates.
 */
@Entity
@Table(name = "periodes_calendrier")
public class PeriodeCalendrier {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    /** TRIMESTRE1, TRIMESTRE2, TRIMESTRE3, VACANCES, FERIE */
    @Column(nullable = false)
    private String type = "VACANCES";

    private String anneeScolaire;

    @Column(nullable = false)
    private LocalDate dateDebut;

    @Column(nullable = false)
    private LocalDate dateFin;

    private Long etablissementId;

    public PeriodeCalendrier() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    /** Un jour dans cette periode doit-il etre exclu du decompte des jours d'ecole ? */
    public boolean isExclusion() {
        return "VACANCES".equals(type) || "FERIE".equals(type);
    }
}
