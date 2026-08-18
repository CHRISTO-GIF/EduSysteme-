package holyflame.administration.repository;

import holyflame.administration.model.AnneeScolaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnneeScolaireRepository extends JpaRepository<AnneeScolaire, Long> {
    List<AnneeScolaire> findByEtablissementIdOrderByLibelleDesc(Long etablissementId);
    Optional<AnneeScolaire> findByEtablissementIdAndLibelle(Long etablissementId, String libelle);
    List<AnneeScolaire> findByEtablissementIdAndStatut(Long etablissementId, String statut);
    boolean existsByEtablissementIdAndLibelle(Long etablissementId, String libelle);
}
