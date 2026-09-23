package holyflame.administration.repository;

import holyflame.administration.model.BaremeQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface BaremeQuestionRepository extends JpaRepository<BaremeQuestion, Long> {
    List<BaremeQuestion> findByExamenIdOrderByOrdreAsc(Long examenId);

    @Modifying @Transactional
    @Query("DELETE FROM BaremeQuestion q WHERE q.examen.id = :examenId")
    void deleteByExamenId(@Param("examenId") Long examenId);
}
