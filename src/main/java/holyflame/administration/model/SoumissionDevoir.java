package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Suivi, par eleve, de l'avancement d'un devoir assigne par un enseignant. */
@Entity
@Table(name = "soumissions_devoirs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"devoir_id", "eleve_id"}))
public class SoumissionDevoir {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devoir_id", nullable = false)
    private Devoir devoir;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eleve_id", nullable = false)
    private Eleve eleve;

    private String statut = "A_FAIRE"; // A_FAIRE, EN_COURS, TERMINE
    private Integer pourcentageAvancement = 0;

    private String fichierPath;
    private String fichierNomOriginal;

    private LocalDateTime dateDebut;
    private LocalDateTime dateSoumission;

    private boolean corrige = false;
    private Double note;
    private String appreciation;
    private LocalDateTime dateCorrection;

    public SoumissionDevoir() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Devoir getDevoir() { return devoir; }
    public void setDevoir(Devoir devoir) { this.devoir = devoir; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public Integer getPourcentageAvancement() { return pourcentageAvancement; }
    public void setPourcentageAvancement(Integer pourcentageAvancement) { this.pourcentageAvancement = pourcentageAvancement; }
    public String getFichierPath() { return fichierPath; }
    public void setFichierPath(String fichierPath) { this.fichierPath = fichierPath; }
    public String getFichierNomOriginal() { return fichierNomOriginal; }
    public void setFichierNomOriginal(String fichierNomOriginal) { this.fichierNomOriginal = fichierNomOriginal; }
    public LocalDateTime getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDateTime dateDebut) { this.dateDebut = dateDebut; }
    public LocalDateTime getDateSoumission() { return dateSoumission; }
    public void setDateSoumission(LocalDateTime dateSoumission) { this.dateSoumission = dateSoumission; }
    public boolean isCorrige() { return corrige; }
    public void setCorrige(boolean corrige) { this.corrige = corrige; }
    public Double getNote() { return note; }
    public void setNote(Double note) { this.note = note; }
    public String getAppreciation() { return appreciation; }
    public void setAppreciation(String appreciation) { this.appreciation = appreciation; }
    public LocalDateTime getDateCorrection() { return dateCorrection; }
    public void setDateCorrection(LocalDateTime dateCorrection) { this.dateCorrection = dateCorrection; }
}
