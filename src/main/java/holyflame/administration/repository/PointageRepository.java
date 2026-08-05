package holyflame.administration.repository;

import holyflame.administration.model.Pointage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PointageRepository extends JpaRepository<Pointage, Long> {
    @Query("SELECT p FROM Pointage p WHERE p.etablissementId = :etabId ORDER BY p.dateHeure DESC")
    List<Pointage> findByEtablissementIdOrderByDateHeureDesc(@Param("etabId") Long etabId);

    @Query("SELECT p FROM Pointage p WHERE p.etablissementId = :etabId AND p.zone.id = :zoneId AND p.dateHeure >= :depuis")
    List<Pointage> findByZoneDepuis(@Param("etabId") Long etabId, @Param("zoneId") Long zoneId, @Param("depuis") LocalDateTime depuis);

    @Query("SELECT COUNT(p) FROM Pointage p WHERE p.etablissementId = :etabId AND p.dateHeure >= :depuis")
    long countByEtablissementIdDepuis(@Param("etabId") Long etabId, @Param("depuis") LocalDateTime depuis);

    long countByZoneId(Long zoneId);
}
