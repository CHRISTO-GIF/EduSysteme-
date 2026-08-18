package holyflame.administration.repository;

import holyflame.administration.model.PeriodeCalendrier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PeriodeCalendrierRepository extends JpaRepository<PeriodeCalendrier, Long> {
    List<PeriodeCalendrier> findByEtablissementIdOrderByDateDebutAsc(Long etablissementId);
    List<PeriodeCalendrier> findByEtablissementIdAndAnneeScolaireOrderByDateDebutAsc(Long etablissementId, String anneeScolaire);

    @Query("SELECT DISTINCT p.anneeScolaire FROM PeriodeCalendrier p WHERE p.etablissementId = :etabId AND p.anneeScolaire IS NOT NULL ORDER BY p.anneeScolaire DESC")
    List<String> findDistinctAnneesScolaires(@Param("etabId") Long etabId);
}
