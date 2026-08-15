package holyflame.administration.repository;

import holyflame.administration.model.JournalAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.PageRequest;

import java.util.List;

public interface JournalActionRepository extends JpaRepository<JournalAction, Long> {
    List<JournalAction> findByEtablissementIdOrderByDateDesc(Long etablissementId, PageRequest pr);
    List<JournalAction> findByEtablissementIdAndModuleOrderByDateDesc(Long etablissementId, String module, PageRequest pr);
    long countByEtablissementId(Long etablissementId);
    long countByEtablissementIdAndModule(Long etablissementId, String module);
    void deleteByEtablissementId(Long etablissementId);

    // Vue restreinte a un seul compte (tous les roles non-admin ne voient que leurs propres actions)
    List<JournalAction> findByEtablissementIdAndUtilisateurEmailOrderByDateDesc(Long etablissementId, String utilisateurEmail, PageRequest pr);
    List<JournalAction> findByEtablissementIdAndModuleAndUtilisateurEmailOrderByDateDesc(Long etablissementId, String module, String utilisateurEmail, PageRequest pr);
    long countByEtablissementIdAndUtilisateurEmail(Long etablissementId, String utilisateurEmail);
    long countByEtablissementIdAndModuleAndUtilisateurEmail(Long etablissementId, String module, String utilisateurEmail);
}
