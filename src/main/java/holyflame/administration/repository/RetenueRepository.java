package holyflame.administration.repository;

import holyflame.administration.model.Retenue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RetenueRepository extends JpaRepository<Retenue, Long> {
    @Query("SELECT r FROM Retenue r WHERE r.etablissementId = :etabId ORDER BY r.dateRetenue ASC, r.heureRetenue ASC")
    List<Retenue> findByEtablissementIdOrderByDateAsc(@Param("etabId") Long etabId);

    @Query("SELECT r FROM Retenue r WHERE r.etablissementId = :etabId AND r.dateRetenue >= :aujourdHui ORDER BY r.dateRetenue ASC, r.heureRetenue ASC")
    List<Retenue> findAVenir(@Param("etabId") Long etabId, @Param("aujourdHui") LocalDate aujourdHui);
}
