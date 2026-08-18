package holyflame.administration.repository;

import holyflame.administration.model.FicheControle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FicheControleRepository extends JpaRepository<FicheControle, Long> {
    List<FicheControle> findByEtablissementIdOrderByDateVisiteDesc(Long etablissementId);
}
