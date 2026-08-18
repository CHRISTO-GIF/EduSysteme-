package holyflame.administration.repository;

import holyflame.administration.model.ConsultationInfirmerie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ConsultationInfirmerieRepository extends JpaRepository<ConsultationInfirmerie, Long> {
    List<ConsultationInfirmerie> findByEtablissementIdOrderByDateHeureDesc(Long etablissementId);
    List<ConsultationInfirmerie> findByEtablissementIdAndStatutOrderByDateHeureDesc(Long etablissementId, String statut);
    List<ConsultationInfirmerie> findByEtablissementIdAndDateHeureBetween(Long etablissementId, LocalDateTime debut, LocalDateTime fin);
    List<ConsultationInfirmerie> findByEleveIdOrderByDateHeureDesc(Long eleveId);
}
