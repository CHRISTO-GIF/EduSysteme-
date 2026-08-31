package holyflame.administration.repository;

import holyflame.administration.model.Departement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartementRepository extends JpaRepository<Departement, Long> {
    List<Departement> findByEtablissementIdOrderByNomAsc(Long etablissementId);
    List<Departement> findByFaculteIdAndEtablissementIdOrderByNomAsc(Long faculteId, Long etablissementId);
    Optional<Departement> findByIdAndEtablissementId(Long id, Long etablissementId);
    Optional<Departement> findByCodeAndEtablissementId(String code, Long etablissementId);
    boolean existsByCodeAndEtablissementId(String code, Long etablissementId);
}
