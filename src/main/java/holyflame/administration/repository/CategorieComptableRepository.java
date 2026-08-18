package holyflame.administration.repository;

import holyflame.administration.model.CategorieComptable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategorieComptableRepository extends JpaRepository<CategorieComptable, Long> {
    List<CategorieComptable> findByEtablissementIdAndActifTrueOrderByCodeAsc(Long etablissementId);
    List<CategorieComptable> findByEtablissementIdOrderByCodeAsc(Long etablissementId);
    Optional<CategorieComptable> findByCodeAndEtablissementId(String code, Long etablissementId);
}
