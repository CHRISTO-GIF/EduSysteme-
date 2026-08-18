package holyflame.administration.repository;

import holyflame.administration.model.MembreEquipePublic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MembreEquipePublicRepository extends JpaRepository<MembreEquipePublic, Long> {
    List<MembreEquipePublic> findByEtablissementIdOrderByOrdreAsc(Long etablissementId);
}
