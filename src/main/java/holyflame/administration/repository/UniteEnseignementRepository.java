package holyflame.administration.repository;

import holyflame.administration.model.UniteEnseignement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UniteEnseignementRepository extends JpaRepository<UniteEnseignement, Long> {
    List<UniteEnseignement> findByEtablissementIdOrderBySemestreAscCodeAsc(Long etablissementId);
    List<UniteEnseignement> findByFiliereIdAndEtablissementIdOrderBySemestreAscCodeAsc(Long filiereId, Long etablissementId);
    List<UniteEnseignement> findByFiliereIdAndSemestreAndEtablissementIdOrderByCodeAsc(Long filiereId, Integer semestre, Long etablissementId);
    Optional<UniteEnseignement> findByIdAndEtablissementId(Long id, Long etablissementId);
    Optional<UniteEnseignement> findByCodeAndEtablissementId(String code, Long etablissementId);
    boolean existsByCodeAndEtablissementId(String code, Long etablissementId);
}
