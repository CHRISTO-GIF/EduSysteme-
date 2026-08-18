package holyflame.administration.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Temoignage affiche sur le site vitrine public (parent, ancien eleve...). Saisie manuelle. */
@Entity
@Table(name = "temoignages_site")
public class TemoignageSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long etablissementId;
    private String auteur;
    private String role; // ex: "Parent d'élève", "Ancien élève"

    @Lob
    private String contenu;

    private LocalDateTime dateAjout;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    public String getAuteur() { return auteur; }
    public void setAuteur(String auteur) { this.auteur = auteur; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContenu() { return contenu; }
    public void setContenu(String contenu) { this.contenu = contenu; }

    public LocalDateTime getDateAjout() { return dateAjout; }
    public void setDateAjout(LocalDateTime dateAjout) { this.dateAjout = dateAjout; }
}
