package holyflame.administration.repository;

import holyflame.administration.model.SoumissionDevoir;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SoumissionDevoirRepository extends JpaRepository<SoumissionDevoir, Long> {
    List<SoumissionDevoir> findByEleveIdOrderByIdDesc(Long eleveId);
    Optional<SoumissionDevoir> findByDevoirIdAndEleveId(Long devoirId, Long eleveId);
    List<SoumissionDevoir> findByDevoirIdOrderByEleveNomAsc(Long devoirId);
    void deleteByDevoirId(Long devoirId);
    void deleteByEleveId(Long eleveId);
}
