package holyflame.administration.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Actualite publiee sur le site vitrine public d'un etablissement. */
@Entity
@Table(name = "actualites_site")
public class ActualiteSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long etablissementId;

    private String titre;

    @Lob
    private String contenu;

    private String imagePath;
    private String statut = "BROUILLON"; // BROUILLON, PUBLIE
    private LocalDateTime dateCreation;
    private LocalDateTime datePublication;
    private Long publieParId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }

    public LocalDateTime getDateCreation() { return dateCreation; }
    public void setDateCreation(LocalDateTime dateCreation) { this.dateCreation = dateCreation; }

    public LocalDateTime getDatePublication() { return datePublication; }
    public void setDatePublication(LocalDateTime datePublication) { this.datePublication = datePublication; }

    public Long getPublieParId() { return publieParId; }
    public void setPublieParId(Long publieParId) { this.publieParId = publieParId; }
}
