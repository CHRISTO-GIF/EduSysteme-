package holyflame.administration.repository;

import holyflame.administration.model.LigneSalaire;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LigneSalaireRepository extends JpaRepository<LigneSalaire, Long> {
    List<LigneSalaire> findBySalaireMensuelIdOrderByOrdreAsc(Long salaireMensuelId);
}
