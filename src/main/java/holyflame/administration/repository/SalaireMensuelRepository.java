package holyflame.administration.repository;

import holyflame.administration.model.SalaireMensuel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalaireMensuelRepository extends JpaRepository<SalaireMensuel, Long> {
    List<SalaireMensuel> findByAnneeOrderByMoisDescPersonnelNomAsc(int annee);
    List<SalaireMensuel> findByPersonnelIdOrderByAnneeDescMoisDesc(Long personnelId);
    List<SalaireMensuel> findByStatut(String statut);
    java.util.Optional<SalaireMensuel> findByPersonnelIdAndMoisAndAnnee(Long personnelId, int mois, int annee);

    @Query("SELECT s FROM SalaireMensuel s WHERE s.personnel.etablissementId = :etabId "
         + "ORDER BY s.annee DESC, s.mois DESC, s.personnel.nom ASC")
    List<SalaireMensuel> findByEtablissementId(@Param("etabId") Long etabId);
}
