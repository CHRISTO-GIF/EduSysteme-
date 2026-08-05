package holyflame.administration.repository;

import holyflame.administration.model.Devoir;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DevoirRepository extends JpaRepository<Devoir, Long> {
    List<Devoir> findByEtablissementIdOrderByDateEcheanceAsc(Long etablissementId);
    List<Devoir> findByEnseignantIdAndEtablissementIdOrderByDateEcheanceAsc(Long enseignantId, Long etablissementId);
    List<Devoir> findByClasseIdAndStatutOrderByDateEcheanceAsc(Long classeId, String statut);
    void deleteByClasseId(Long classeId);
    void deleteByMatiereId(Long matiereId);
}
