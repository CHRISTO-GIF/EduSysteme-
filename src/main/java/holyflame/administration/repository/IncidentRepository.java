package holyflame.administration.repository;

import holyflame.administration.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    @Query("SELECT i FROM Incident i WHERE i.etablissementId = :etabId ORDER BY i.dateHeure DESC")
    List<Incident> findByEtablissementIdOrderByDateHeureDesc(@Param("etabId") Long etabId);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.etablissementId = :etabId AND i.dateHeure >= :debut")
    long countByEtablissementIdDepuis(@Param("etabId") Long etabId, @Param("debut") LocalDateTime debut);
}
