package holyflame.administration.repository;

import holyflame.administration.model.Conduite;
import holyflame.administration.model.Eleve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConduiteRepository extends JpaRepository<Conduite, Long> {
    Optional<Conduite> findByEleveAndTrimestreAndAnneeScolaire(Eleve eleve, Integer trimestre, String anneeScolaire);
}
