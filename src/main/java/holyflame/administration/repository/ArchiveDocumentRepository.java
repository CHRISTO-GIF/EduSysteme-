package holyflame.administration.repository;

import holyflame.administration.model.ArchiveDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ArchiveDocumentRepository extends JpaRepository<ArchiveDocument, Long> {
    List<ArchiveDocument> findByEtablissementIdOrderByDateArchiveDesc(Long etablissementId);
    List<ArchiveDocument> findByEtablissementIdAndCategorieOrderByDateArchiveDesc(Long etablissementId, String categorie);
    long countByEtablissementId(Long etablissementId);

    @Query("SELECT a FROM ArchiveDocument a WHERE a.etablissementId = :etabId "
         + "AND (LOWER(a.nom) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(a.tags) LIKE LOWER(CONCAT('%', :q, '%'))) "
         + "ORDER BY a.dateArchive DESC")
    List<ArchiveDocument> rechercher(@Param("etabId") Long etabId, @Param("q") String q);

    // Migration : associer les documents historiques sans etabId a l'etablissement du premier admin qui se connecte
    @Modifying
    @Transactional
    @Query("UPDATE ArchiveDocument a SET a.etablissementId = :etabId WHERE a.etablissementId IS NULL")
    int migrateNullEtablissementId(@Param("etabId") Long etabId);
}
