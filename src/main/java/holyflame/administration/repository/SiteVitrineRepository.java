package holyflame.administration.repository;

import holyflame.administration.model.SiteVitrine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SiteVitrineRepository extends JpaRepository<SiteVitrine, Long> {
    Optional<SiteVitrine> findByEtablissementId(Long etablissementId);
    Optional<SiteVitrine> findBySlugAndActifTrue(String slug);
    Optional<SiteVitrine> findBySlug(String slug);
}
