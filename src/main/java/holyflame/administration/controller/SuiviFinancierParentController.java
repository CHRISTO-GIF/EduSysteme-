package holyflame.administration.controller;

import holyflame.administration.model.Eleve;
import holyflame.administration.model.FraisScolarite;
import holyflame.administration.model.Paiement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.FraisScolariteRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FinanceParentService;
import holyflame.administration.service.MessagerieService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/portail-parent/paiements")
public class SuiviFinancierParentController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private FraisScolariteRepository fraisScolariteRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;
    @Autowired private MessagerieService messagerieService;
    @Autowired private FinanceParentService financeParentService;

    @GetMapping
    public String index(Authentication auth, Model model) {
        String email = auth != null ? auth.getName() : "";
        List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(email);
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null && !enfants.isEmpty()) etabId = enfants.get(0).getEtablissementId();

        FinanceParentService.ResumeSolde resume = financeParentService.calculerResume(enfants, etabId);

        List<Paiement> tousPaiements = new ArrayList<>();
        for (Eleve e : enfants) tousPaiements.addAll(paiementRepository.findByEleveId(e.getId()));

        List<Paiement> derniersPaiements = tousPaiements.stream()
            .sorted(Comparator.comparing(Paiement::getDatePaiement, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(5)
            .toList();

        List<FraisScolarite> tousFrais = etabId != null
            ? fraisScolariteRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId)
            : List.of();
        List<String> categories = tousFrais.stream().map(FraisScolarite::getTypeFrais)
            .filter(Objects::nonNull).distinct().toList();

        model.addAttribute("enfants", enfants);
        model.addAttribute("lignes", resume.lignes);
        model.addAttribute("derniersPaiements", derniersPaiements);
        model.addAttribute("soldeTotalARegler", resume.soldeTotalARegler);
        model.addAttribute("nbEnRetard", resume.nbEnRetard);
        model.addAttribute("prochaineEcheance", resume.prochaineEcheance);
        model.addAttribute("categories", categories);
        model.addAttribute("adminEmail", trouverEmailAdministration(etabId));
        model.addAttribute("emailParent", email);
        return "portail-parent-paiements";
    }

    @GetMapping("/releve")
    public String releveAnnuel(Authentication auth, Model model) {
        String email = auth != null ? auth.getName() : "";
        List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(email);

        List<Paiement> tousPaiements = new ArrayList<>();
        for (Eleve e : enfants) tousPaiements.addAll(paiementRepository.findByEleveId(e.getId()));
        tousPaiements.sort(Comparator.comparing(Paiement::getDatePaiement, Comparator.nullsLast(Comparator.naturalOrder())));

        double total = tousPaiements.stream().mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();

        model.addAttribute("enfants", enfants);
        model.addAttribute("paiements", tousPaiements);
        model.addAttribute("total", total);
        model.addAttribute("anneeCourante", horlogeService.aujourdHui().getYear());
        model.addAttribute("emailParent", email);
        return "portail-parent-releve";
    }

    @PostMapping("/demander")
    public String demander(@RequestParam Long fraisId, @RequestParam Long eleveId, Authentication auth, RedirectAttributes ra) throws IOException {
        String email = auth != null ? auth.getName() : "";
        FraisScolarite frais = fraisScolariteRepository.findById(fraisId).orElse(null);
        // L'eleve doit etre l'un des propres enfants du parent connecte — sans ce controle,
        // n'importe quel eleveId permettait de faire referencer le nom d'un enfant d'une
        // autre famille dans une demande de paiement.
        Eleve eleve = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(email).stream()
            .filter(e -> e.getId().equals(eleveId))
            .findFirst().orElse(null);
        if (frais == null || eleve == null
                || !java.util.Objects.equals(frais.getEtablissementId(), eleve.getEtablissementId())) {
            ra.addFlashAttribute("erreurMsg", "Frais ou élève introuvable.");
            return "redirect:/portail-parent/paiements";
        }

        String adminEmail = trouverEmailAdministration(eleve.getEtablissementId());

        if (adminEmail == null) {
            ra.addFlashAttribute("erreurMsg", "Aucun contact de l'administration n'est disponible pour le moment.");
            return "redirect:/portail-parent/paiements";
        }

        String message = "Bonjour, je souhaite regler : " + frais.getDesignation()
            + " (" + eleve.getPrenom() + " " + eleve.getNom() + ") - Montant : "
            + (frais.getMontant() != null ? frais.getMontant().longValue() : 0) + " F. "
            + "Merci de me contacter pour la marche a suivre.";
        messagerieService.envoyerMessage(email, adminEmail, message, null, eleve.getEtablissementId());

        ra.addFlashAttribute("successMsg", "Votre demande de paiement a ete transmise a l'administration via la messagerie.");
        return "redirect:/portail-parent/paiements";
    }

    private String trouverEmailAdministration(Long etabId) {
        if (etabId == null) return null;
        return utilisateurRepository.findByEtablissementId(etabId).stream()
            .filter(u -> "ADMIN".equals(u.getRole()) || "SECRETAIRE".equals(u.getRole()))
            .map(Utilisateur::getEmail)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }
}
