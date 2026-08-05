package holyflame.administration.repository;

import holyflame.administration.model.TachePersonnelle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TachePersonnelleRepository extends JpaRepository<TachePersonnelle, Long> {
    List<TachePersonnelle> findByEleveIdOrderByDateCreationDesc(Long eleveId);
    void deleteByIdAndEleveId(Long id, Long eleveId);
}
