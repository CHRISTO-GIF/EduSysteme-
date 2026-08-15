package holyflame.administration.controller;

import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.JournalActionRepository;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/journal")
public class JournalController {

    private static final int TAILLE_PAGE = 20;

    @Autowired private JournalActionRepository journalRepository;
    @Autowired private EtablissementService etablissementService;

    @GetMapping
    public String journal(@RequestParam(required = false) String module,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        boolean filtreModule = module != null && !module.isBlank();

        // Seul l'ADMIN voit l'activite de tout l'etablissement ; les autres roles ne voient
        // que les actions qu'ils ont eux-memes effectuees (evite d'exposer les actions
        // financieres/RH/pedagogiques d'autres comptes a qui n'a pas a les consulter).
        Utilisateur utilisateurConnecte = etablissementService.getCurrentUtilisateur();
        boolean estAdmin = utilisateurConnecte != null && "ADMIN".equals(utilisateurConnecte.getRole());
        String monEmail = utilisateurConnecte != null ? utilisateurConnecte.getEmail() : "";

        long total = estAdmin
            ? (filtreModule ? journalRepository.countByEtablissementIdAndModule(etabId, module) : journalRepository.countByEtablissementId(etabId))
            : (filtreModule ? journalRepository.countByEtablissementIdAndModuleAndUtilisateurEmail(etabId, module, monEmail) : journalRepository.countByEtablissementIdAndUtilisateurEmail(etabId, monEmail));
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) TAILLE_PAGE));
        int pageCourante = Math.max(0, Math.min(page, totalPages - 1));

        PageRequest pr = PageRequest.of(pageCourante, TAILLE_PAGE);
        var actions = estAdmin
            ? (filtreModule ? journalRepository.findByEtablissementIdAndModuleOrderByDateDesc(etabId, module, pr) : journalRepository.findByEtablissementIdOrderByDateDesc(etabId, pr))
            : (filtreModule ? journalRepository.findByEtablissementIdAndModuleAndUtilisateurEmailOrderByDateDesc(etabId, module, monEmail, pr) : journalRepository.findByEtablissementIdAndUtilisateurEmailOrderByDateDesc(etabId, monEmail, pr));

        model.addAttribute("estAdmin", estAdmin);
        model.addAttribute("actions", actions);
        model.addAttribute("module", module);
        model.addAttribute("modules", java.util.List.of(
            "ELEVES", "FINANCES", "NOTES", "ABSENCES", "RH", "PARAMETRES", "COMMUNICATION", "BULLETINS"
        ));
        int debutFenetre = Math.max(0, pageCourante - 2);
        int finFenetre = Math.min(totalPages - 1, pageCourante + 2);
        java.util.List<Integer> pagesAffichees = new java.util.ArrayList<>();
        for (int i = debutFenetre; i <= finFenetre; i++) pagesAffichees.add(i);

        model.addAttribute("page", pageCourante);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalActions", total);
        model.addAttribute("pagesAffichees", pagesAffichees);
        model.addAttribute("premierElement", total == 0 ? 0 : pageCourante * TAILLE_PAGE + 1);
        model.addAttribute("dernierElement", Math.min(total, (long) (pageCourante + 1) * TAILLE_PAGE));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "journal";
    }
}
