package holyflame.administration.repository;

import holyflame.administration.model.BaremeQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BaremeQuestionRepository extends JpaRepository<BaremeQuestion, Long> {
    List<BaremeQuestion> findByExamenIdOrderByOrdreAsc(Long examenId);
    void deleteByExamenId(Long examenId);
}
