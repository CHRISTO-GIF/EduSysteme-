package holyflame.administration.repository;

import holyflame.administration.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<Zone, Long> {
    List<Zone> findByEtablissementIdOrderByNomAsc(Long etablissementId);
}
