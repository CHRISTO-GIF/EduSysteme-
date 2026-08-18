package holyflame.administration.repository;

import holyflame.administration.model.AvisParent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AvisParentRepository extends JpaRepository<AvisParent, Long> {
    List<AvisParent> findByEtablissementIdOrderByDateSignalementDesc(Long etablissementId);
    List<AvisParent> findByEleveIdOrderByDateSignalementDesc(Long eleveId);
}
