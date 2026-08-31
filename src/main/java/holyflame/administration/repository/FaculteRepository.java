package holyflame.administration.repository;

import holyflame.administration.model.Faculte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FaculteRepository extends JpaRepository<Faculte, Long> {
    List<Faculte> findByEtablissementIdOrderByNomAsc(Long etablissementId);
    Optional<Faculte> findByIdAndEtablissementId(Long id, Long etablissementId);
    Optional<Faculte> findByCodeAndEtablissementId(String code, Long etablissementId);
    boolean existsByCodeAndEtablissementId(String code, Long etablissementId);
}
