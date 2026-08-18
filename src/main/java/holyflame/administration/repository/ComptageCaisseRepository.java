package holyflame.administration.repository;

import holyflame.administration.model.ComptageCaisse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComptageCaisseRepository extends JpaRepository<ComptageCaisse, Long> {
    List<ComptageCaisse> findByEtablissementIdOrderByDateComptageDesc(Long etablissementId);
}
