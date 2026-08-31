package holyflame.administration.repository;

import holyflame.administration.model.Filiere;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FiliereRepository extends JpaRepository<Filiere, Long> {
    List<Filiere> findByEtablissementIdOrderByNomAsc(Long etablissementId);
    List<Filiere> findByFaculteIdAndEtablissementIdOrderByNomAsc(Long faculteId, Long etablissementId);
    List<Filiere> findByDepartementIdAndEtablissementIdOrderByNomAsc(Long departementId, Long etablissementId);
    Optional<Filiere> findByIdAndEtablissementId(Long id, Long etablissementId);
    Optional<Filiere> findByCodeAndEtablissementId(String code, Long etablissementId);
    boolean existsByCodeAndEtablissementId(String code, Long etablissementId);
}
