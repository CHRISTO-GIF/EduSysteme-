package holyflame.administration.repository;

import holyflame.administration.model.ConditionMedicale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConditionMedicaleRepository extends JpaRepository<ConditionMedicale, Long> {
    List<ConditionMedicale> findByEtablissementIdAndActifTrueOrderByLibelleAsc(Long etablissementId);
    List<ConditionMedicale> findByEleveIdAndActifTrueOrderByLibelleAsc(Long eleveId);
}
