package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "programmes")
public class Programme {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titre;

    @Column(length = 3000)
    private String contenu;

    private LocalDate dateDebut;
    private LocalDate dateFin;
    private String statut;  // BROUILLON, PUBLIE
    private String auteur;
    private Long etablissementId;
    private String anneeScolaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classe_id")
    private Classe classe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matiere_id")
    private Matiere matiere;

    private Integer heuresEstimees;
    private String frequence; // HEBDOMADAIRE, BI_HEBDOMADAIRE, MENSUEL, INTENSIF

    @Column(length = 2000)
    private String objectifs;

    private Long enseignantId; // reference vers Personnel.id

    private String syllabusPath;
    private String syllabusNomOriginal;

    public Programme() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }
    public LocalDate getDateDebut() { return dateDebut; }
    public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }
    public LocalDate getDateFin() { return dateFin; }
    public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getAuteur() { return auteur; }
    public void setAuteur(String auteur) { this.auteur = auteur; }
    public Classe getClasse() { return classe; }
    public void setClasse(Classe classe) { this.classe = classe; }
    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }
    public Matiere getMatiere() { return matiere; }
    public void setMatiere(Matiere matiere) { this.matiere = matiere; }
    public Integer getHeuresEstimees() { return heuresEstimees; }
    public void setHeuresEstimees(Integer heuresEstimees) { this.heuresEstimees = heuresEstimees; }
    public String getFrequence() { return frequence; }
    public void setFrequence(String frequence) { this.frequence = frequence; }
    public String getObjectifs() { return objectifs; }
    public void setObjectifs(String objectifs) { this.objectifs = objectifs; }
    public Long getEnseignantId() { return enseignantId; }
    public void setEnseignantId(Long enseignantId) { this.enseignantId = enseignantId; }
    public String getSyllabusPath() { return syllabusPath; }
    public void setSyllabusPath(String syllabusPath) { this.syllabusPath = syllabusPath; }
    public String getSyllabusNomOriginal() { return syllabusNomOriginal; }
    public void setSyllabusNomOriginal(String syllabusNomOriginal) { this.syllabusNomOriginal = syllabusNomOriginal; }
    public String getAnneeScolaire() { return anneeScolaire; }
    public void setAnneeScolaire(String anneeScolaire) { this.anneeScolaire = anneeScolaire; }
}
