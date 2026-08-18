package holyflame.administration.repository;

import holyflame.administration.model.ArriereEleve;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArriereEleveRepository extends JpaRepository<ArriereEleve, Long> {
    List<ArriereEleve> findByEleveIdOrderByAnneeScolaireOrigineDesc(Long eleveId);
    List<ArriereEleve> findByEtablissementIdOrderByAnneeScolaireOrigineDesc(Long etablissementId);
}
