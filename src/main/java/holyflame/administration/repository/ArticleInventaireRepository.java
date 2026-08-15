package holyflame.administration.repository;

import holyflame.administration.model.ArticleInventaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ArticleInventaireRepository extends JpaRepository<ArticleInventaire, Long> {
    List<ArticleInventaire> findByEtablissementIdOrderByCategorieAscNomAsc(Long etablissementId);
    long countByEtatAndEtablissementId(String etat, Long etablissementId);

    // Migration : associer les articles historiques sans etabId a l'etablissement du premier admin qui se connecte
    @Modifying
    @Transactional
    @Query("UPDATE ArticleInventaire a SET a.etablissementId = :etabId WHERE a.etablissementId IS NULL")
    int migrateNullEtablissementId(@Param("etabId") Long etabId);
}
