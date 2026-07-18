package holyflame.administration.repository;

import holyflame.administration.model.Retard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RetardRepository extends JpaRepository<Retard, Long> {
    @Query("SELECT r FROM Retard r WHERE r.eleve.etablissementId = :etabId ORDER BY r.date DESC, r.saisieAt DESC")
    List<Retard> findByEtablissementIdOrderByDateDesc(@Param("etabId") Long etabId);

    List<Retard> findByEleveIdOrderByDateDesc(Long eleveId);

    @Query("SELECT r FROM Retard r WHERE r.eleve.etablissementId = :etabId AND r.date = :date")
    List<Retard> findByEtablissementIdAndDate(@Param("etabId") Long etabId, @Param("date") LocalDate date);

    @Modifying
    @Query("DELETE FROM Retard r WHERE r.eleve.id = :id")
    void deleteByEleveId(@Param("id") Long eleveId);
}
