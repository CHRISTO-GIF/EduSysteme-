package holyflame.administration.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Membre de l'equipe mis en avant sur le site vitrine public. Fiche distincte et volontairement
 * separee du module Personnel/RH interne : seules les informations que l'etablissement choisit
 * explicitement de rendre publiques transitent ici — jamais de donnee RH reelle.
 */
@Entity
@Table(name = "membres_equipe_public")
public class MembreEquipePublic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long etablissementId;
    private String nom;
    private String fonction;
    private String bio;
    private String photoPath;
    private Integer ordre = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getFonction() { return fonction; }
    public void setFonction(String fonction) { this.fonction = fonction; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }

    public Integer getOrdre() { return ordre; }
    public void setOrdre(Integer ordre) { this.ordre = ordre; }
}
