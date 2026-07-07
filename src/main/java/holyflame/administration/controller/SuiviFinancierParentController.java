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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/portail-parent/paiements")
public class SuiviFinancierParentController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private FraisScolariteRepository fraisScolariteRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private MessagerieService messagerieService;

    private static final Map<String, Integer> MOIS_FR = Map.ofEntries(
        Map.entry("janvier", 1), Map.entry("fevrier", 2), Map.entry("février", 2), Map.entry("mars", 3),
        Map.entry("avril", 4), Map.entry("mai", 5), Map.entry("juin", 6), Map.entry("juillet", 7),
        Map.entry("aout", 8), Map.entry("août", 8), Map.entry("septembre", 9), Map.entry("octobre", 10),
        Map.entry("novembre", 11), Map.entry("decembre", 12), Map.entry("décembre", 12)
    );

    @GetMapping
    public String index(Authentication auth, Model model) {
        String email = auth != null ? auth.getName() : "";
        List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(email);
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null && !enfants.isEmpty()) etabId = enfants.get(0).getEtablissementId();

        List<FraisScolarite> tousFrais = etabId != null
            ? fraisScolariteRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId)
            : List.of();

        List<Paiement> tousPaiements = new ArrayList<>();
        for (Eleve e : enfants) tousPaiements.addAll(paiementRepository.findByEleveId(e.getId()));

        LocalDate aujourdHui = LocalDate.now();
        List<Map<String, Object>> lignes = new ArrayList<>();
        double soldeTotalARegler = 0;
        int nbEnRetard = 0;
        LocalDate prochaineEcheance = null;

        for (Eleve enfant : enfants) {
            if (enfant.getClasse() == null) continue;
            String niveau = enfant.getClasse().getNiveau();
            List<FraisScolarite> applicables = tousFrais.stream()
                .filter(f -> f.getNiveauCible() == null || f.getNiveauCible().isBlank()
                    || f.getNiveauCible().equalsIgnoreCase(niveau))
                .toList();

            for (FraisScolarite f : applicables) {
                double montant = f.getMontant() != null ? f.getMontant() : 0;
                double verse = tousPaiements.stream()
                    .filter(p -> p.getEleve() != null && p.getEleve().getId().equals(enfant.getId())
                        && p.getFraisScolarite() != null && p.getFraisScolarite().getId().equals(f.getId()))
                    .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0)
                    .sum();
                LocalDate echeanceDate = calculerEcheance(f.getEcheance(), enfant.getClasse().getAnneeScolaire());

                String statut;
                if (montant > 0 && verse >= montant) statut = "PAYE";
                else if (echeanceDate != null && echeanceDate.isBefore(aujourdHui)) statut = "EN_RETARD";
                else statut = "EN_ATTENTE";

                if (!"PAYE".equals(statut)) {
                    soldeTotalARegler += (montant - verse);
                    if ("EN_RETARD".equals(statut)) nbEnRetard++;
                    if (echeanceDate != null && (prochaineEcheance == null || echeanceDate.isBefore(prochaineEcheance))) {
                        prochaineEcheance = echeanceDate;
                    }
                }

                Map<String, Object> ligne = new LinkedHashMap<>();
                ligne.put("designation", f.getDesignation());
                ligne.put("categorie", f.getTypeFrais());
                ligne.put("enfant", enfant);
                ligne.put("montant", montant);
                ligne.put("resteAPayer", Math.max(0, montant - verse));
                ligne.put("echeanceDate", echeanceDate);
                ligne.put("statut", statut);
                ligne.put("fraisId", f.getId());
                ligne.put("eleveId", enfant.getId());
                lignes.add(ligne);
            }
        }

        lignes.sort(Comparator.comparing(
            (Map<String, Object> l) -> "PAYE".equals(l.get("statut")) ? 1 : 0
        ).thenComparing(l -> (LocalDate) l.get("echeanceDate"), Comparator.nullsLast(Comparator.naturalOrder())));

        List<Paiement> derniersPaiements = tousPaiements.stream()
            .sorted(Comparator.comparing(Paiement::getDatePaiement, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(5)
            .toList();

        List<String> categories = tousFrais.stream().map(FraisScolarite::getTypeFrais)
            .filter(Objects::nonNull).distinct().toList();

        model.addAttribute("enfants", enfants);
        model.addAttribute("lignes", lignes);
        model.addAttribute("derniersPaiements", derniersPaiements);
        model.addAttribute("soldeTotalARegler", soldeTotalARegler);
        model.addAttribute("nbEnRetard", nbEnRetard);
        model.addAttribute("prochaineEcheance", prochaineEcheance);
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
        model.addAttribute("anneeCourante", LocalDate.now().getYear());
        model.addAttribute("emailParent", email);
        return "portail-parent-releve";
    }

    @PostMapping("/demander")
    public String demander(@RequestParam Long fraisId, @RequestParam Long eleveId, Authentication auth, RedirectAttributes ra) throws IOException {
        String email = auth != null ? auth.getName() : "";
        FraisScolarite frais = fraisScolariteRepository.findById(fraisId).orElse(null);
        Eleve eleve = eleveRepository.findById(eleveId).orElse(null);
        if (frais == null || eleve == null) {
            ra.addFlashAttribute("erreurMsg", "Frais introuvable.");
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
        messagerieService.envoyerMessage(email, adminEmail, message, null);

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

    private LocalDate calculerEcheance(String echeance, String anneeScolaire) {
        if (echeance == null || anneeScolaire == null) return null;
        Integer mois = MOIS_FR.get(echeance.trim().toLowerCase());
        if (mois == null) return null;
        int anneeDebut;
        try {
            anneeDebut = Integer.parseInt(anneeScolaire.split("-")[0].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        int annee = mois >= 9 ? anneeDebut : anneeDebut + 1;
        return LocalDate.of(annee, mois, 5);
    }
}
