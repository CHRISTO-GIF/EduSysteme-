package holyflame.administration.repository;

import holyflame.administration.model.TemoignageSite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemoignageSiteRepository extends JpaRepository<TemoignageSite, Long> {
    List<TemoignageSite> findByEtablissementIdOrderByDateAjoutDesc(Long etablissementId);
}
