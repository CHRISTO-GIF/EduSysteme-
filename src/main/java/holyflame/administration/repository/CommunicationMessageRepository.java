package holyflame.administration.repository;

import holyflame.administration.model.CommunicationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CommunicationMessageRepository extends JpaRepository<CommunicationMessage, Long> {
    List<CommunicationMessage> findAllByOrderByDateEnvoiDesc();
    List<CommunicationMessage> findByStatutOrderByDateEnvoiDesc(String statut);
    List<CommunicationMessage> findByEtablissementIdOrderByDateEnvoiDesc(Long etablissementId);
    long countByStatutAndEtablissementId(String statut, Long etablissementId);

    // Migration : associer les messages historiques sans etabId a l'etablissement du premier admin qui se connecte
    @Modifying
    @Transactional
    @Query("UPDATE CommunicationMessage c SET c.etablissementId = :etabId WHERE c.etablissementId IS NULL")
    int migrateNullEtablissementId(@Param("etabId") Long etabId);
}
