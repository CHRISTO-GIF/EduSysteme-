package holyflame.administration.repository;

import holyflame.administration.model.SignalementMessagerie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignalementMessagerieRepository extends JpaRepository<SignalementMessagerie, Long> {
    List<SignalementMessagerie> findByEtablissementIdOrderByDateSignalementDesc(Long etablissementId);
}
