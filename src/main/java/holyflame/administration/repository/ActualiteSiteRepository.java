package holyflame.administration.repository;

import holyflame.administration.model.ActualiteSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActualiteSiteRepository extends JpaRepository<ActualiteSite, Long> {
    List<ActualiteSite> findByEtablissementIdOrderByDateCreationDesc(Long etablissementId);
    List<ActualiteSite> findByEtablissementIdAndStatutOrderByDatePublicationDesc(Long etablissementId, String statut);
}
