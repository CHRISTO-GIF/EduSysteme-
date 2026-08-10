package holyflame.administration.repository;

import holyflame.administration.model.MembreJury;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MembreJuryRepository extends JpaRepository<MembreJury, Long> {
    List<MembreJury> findBySeanceDeliberationIdOrderByIdAsc(Long seanceDeliberationId);

    @Modifying @Transactional
    @Query("DELETE FROM MembreJury m WHERE m.id = :id AND m.seanceDeliberation.id = :seanceId")
    void deleteByIdAndSeanceDeliberationId(@Param("id") Long id, @Param("seanceId") Long seanceId);
}
