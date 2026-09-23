package holyflame.administration.controller;

import holyflame.administration.model.Publication;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.PublicationRepository;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/publications")
public class PublicationController {

    @Autowired private PublicationRepository publicationRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;

    @GetMapping
    public String index(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("publications",
            publicationRepository.findByEtablissementIdOrderByDatePublicationDesc(etabId));
        model.addAttribute("classes",
            classeRepository.findByEtablissementId(etabId));
        return "publications";
    }

    @PostMapping
    public String publier(@RequestParam String titre,
                          @RequestParam String contenu,
                          @RequestParam String categorie,
                          @RequestParam(required = false) Long classeId,
                          Authentication auth,
                          RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();

        Publication pub = new Publication();
        pub.setTitre(titre.trim());
        pub.setContenu(contenu.trim());
        pub.setCategorie(categorie);
        // Classe cible : ignoree si elle n'appartient pas a l'etablissement (null = tous les eleves)
        pub.setClasseId(classeId != null && classeRepository.findById(classeId)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId())).isPresent() ? classeId : null);
        pub.setEtablissementId(etabId);
        pub.setDatePublication(horlogeService.maintenant());
        pub.setPubliePar(auth != null ? auth.getName() : "Admin");
        publicationRepository.save(pub);

        ra.addFlashAttribute("successMsg", "Publication envoyée avec succès.");
        return "redirect:/publications";
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id, RedirectAttributes ra) {
        // Uniquement une publication de l'etablissement courant (l'id vient de l'URL)
        Long etabId = etablissementService.getCurrentEtablissementId();
        publicationRepository.findById(id)
            .filter(p -> etabId != null && etabId.equals(p.getEtablissementId()))
            .ifPresent(publicationRepository::delete);
        ra.addFlashAttribute("successMsg", "Publication supprimée.");
        return "redirect:/publications";
    }
}
