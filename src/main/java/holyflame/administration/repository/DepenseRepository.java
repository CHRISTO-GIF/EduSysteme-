package holyflame.administration.repository;

import holyflame.administration.model.Depense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepenseRepository extends JpaRepository<Depense, Long> {
    List<Depense> findByEtablissementIdOrderByDateDepenseDesc(Long etablissementId);
}
