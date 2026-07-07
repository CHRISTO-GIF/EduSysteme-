package holyflame.administration.repository;

import holyflame.administration.model.EvenementEcole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvenementEcoleRepository extends JpaRepository<EvenementEcole, Long> {
    List<EvenementEcole> findByEtablissementIdOrderByDateEvenementAsc(Long etablissementId);
}
