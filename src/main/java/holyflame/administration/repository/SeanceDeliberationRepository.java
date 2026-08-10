package holyflame.administration.repository;

import holyflame.administration.model.SeanceDeliberation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeanceDeliberationRepository extends JpaRepository<SeanceDeliberation, Long> {
    List<SeanceDeliberation> findByEtablissementIdOrderByDateSeanceDesc(Long etablissementId);
    List<SeanceDeliberation> findByClasseIdAndAnneeScolaireOrderByDateSeanceDesc(Long classeId, String anneeScolaire);
}
