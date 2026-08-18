package holyflame.administration.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Evenement affiche sur le site vitrine public (portes ouvertes, remise de diplomes...).
 * Distinct du calendrier interne EvenementEcole : curation volontaire pour le grand public,
 * jamais derive automatiquement du planning interne (reunions, conseils de classe...).
 */
@Entity
@Table(name = "evenements_publics")
public class EvenementPublic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long etablissementId;
    private String titre;
    private String description;
    private LocalDate dateEvenement;
    private String lieu;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEtablissementId() { return etablissementId; }
    public void setEtablissementId(Long etablissementId) { this.etablissementId = etablissementId; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getDateEvenement() { return dateEvenement; }
    public void setDateEvenement(LocalDate dateEvenement) { this.dateEvenement = dateEvenement; }

    public String getLieu() { return lieu; }
    public void setLieu(String lieu) { this.lieu = lieu; }
}
