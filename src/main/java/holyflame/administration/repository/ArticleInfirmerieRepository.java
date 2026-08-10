package holyflame.administration.repository;

import holyflame.administration.model.ArticleInfirmerie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleInfirmerieRepository extends JpaRepository<ArticleInfirmerie, Long> {
    List<ArticleInfirmerie> findByEtablissementIdOrderByDesignationAsc(Long etablissementId);
}
