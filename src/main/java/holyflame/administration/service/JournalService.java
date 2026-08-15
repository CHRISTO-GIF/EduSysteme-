package holyflame.administration.service;

import holyflame.administration.model.JournalAction;
import holyflame.administration.repository.JournalActionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class JournalService {

    @Autowired private JournalActionRepository journalRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private HorlogeService horlogeService;

    public void log(String action, String module, String detail) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth != null ? auth.getName() : "système";

            JournalAction entry = new JournalAction();
            entry.setUtilisateurEmail(email);
            entry.setUtilisateurNom(email);
            entry.setAction(action);
            entry.setModule(module);
            entry.setDetail(detail);
            entry.setEtablissementId(etablissementService.getCurrentEtablissementId());
            entry.setDate(horlogeService.maintenant());
            journalRepository.save(entry);
        } catch (Exception ignored) {
            // Ne jamais bloquer l'action principale à cause du journal
        }
    }
}
