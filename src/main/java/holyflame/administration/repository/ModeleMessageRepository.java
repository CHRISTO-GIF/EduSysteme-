package holyflame.administration.repository;

import holyflame.administration.model.ModeleMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ModeleMessageRepository extends JpaRepository<ModeleMessage, Long> {
    List<ModeleMessage> findAllByOrderByTypeAscNomAsc();
    List<ModeleMessage> findByEtablissementIdOrderByTypeAscNomAsc(Long etablissementId);

    // Migration : associer les modeles historiques sans etabId a l'etablissement du premier admin qui se connecte
    @Modifying
    @Transactional
    @Query("UPDATE ModeleMessage m SET m.etablissementId = :etabId WHERE m.etablissementId IS NULL")
    int migrateNullEtablissementId(@Param("etabId") Long etabId);
}
