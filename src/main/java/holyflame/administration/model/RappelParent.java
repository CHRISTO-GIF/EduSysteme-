package holyflame.administration.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "rappels_parent")
public class RappelParent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String parentEmail;

    @Column(nullable = false)
    private String titre;

    private LocalDate dateRappel;
    private String notes;

    public RappelParent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getParentEmail() { return parentEmail; }
    public void setParentEmail(String parentEmail) { this.parentEmail = parentEmail; }
    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre; }
    public LocalDate getDateRappel() { return dateRappel; }
    public void setDateRappel(LocalDate dateRappel) { this.dateRappel = dateRappel; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
