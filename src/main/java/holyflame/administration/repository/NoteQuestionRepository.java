package holyflame.administration.repository;

import holyflame.administration.model.NoteQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NoteQuestionRepository extends JpaRepository<NoteQuestion, Long> {
    List<NoteQuestion> findByExamenIdAndEleveId(Long examenId, Long eleveId);

    Optional<NoteQuestion> findByBaremeQuestionIdAndEleveId(Long baremeQuestionId, Long eleveId);

    @Modifying
    @Query("DELETE FROM NoteQuestion nq WHERE nq.examen.id = :examenId")
    void deleteByExamenId(@Param("examenId") Long examenId);
}
