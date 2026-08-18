package holyflame.administration.repository;

import holyflame.administration.model.DecisionPassage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface DecisionPassageRepository extends JpaRepository<DecisionPassage, Long> {
    List<DecisionPassage> findByClasseOrigineIdAndAnneeScolaire(Long classeId, String anneeScolaire);
    Optional<DecisionPassage> findByEleveIdAndAnneeScolaire(Long eleveId, String anneeScolaire);

    @Modifying @Transactional
    @Query("DELETE FROM DecisionPassage d WHERE d.eleve.id = :eleveId")
    void deleteByEleveId(@Param("eleveId") Long eleveId);
}
