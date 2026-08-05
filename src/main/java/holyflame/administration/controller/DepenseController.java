package holyflame.administration.controller;

import holyflame.administration.model.Depense;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.LigneBudget;
import holyflame.administration.repository.DepenseRepository;
import holyflame.administration.repository.LigneBudgetRepository;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.FileStorageService;
import holyflame.administration.service.JournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/finances/depenses")
public class DepenseController {

    @Autowired private DepenseRepository depenseRepository;
    @Autowired private LigneBudgetRepository budgetRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private JournalService journalService;
    @Autowired private FileStorageService fileStorageService;

    @GetMapping
    public String index(
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) String statut,
            Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        LocalDate aujourdHui = LocalDate.now();
        int moisFiltre = mois != null ? mois : aujourdHui.getMonthValue();
        int anneeFiltre = annee != null ? annee : aujourdHui.getYear();

        List<Depense> toutesLesDepenses = depenseRepository.findByEtablissementIdOrderByDateDepenseDesc(etabId);

        List<Depense> depensesDuMois = toutesLesDepenses.stream()
            .filter(d -> d.getDateDepense() != null
                && d.getDateDepense().getMonthValue() == moisFiltre
                && d.getDateDepense().getYear() == anneeFiltre)
            .collect(Collectors.toList());

        List<Depense> depensesFiltrees = depensesDuMois.stream()
            .filter(d -> categorie == null || categorie.isBlank() || categorie.equals(d.getCategorie()))
            .filter(d -> statut == null || statut.isBlank() || statut.equals(d.getStatut()))
            .collect(Collectors.toList());

        double totalDuMois = depensesDuMois.stream().mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();

        LocalDate moisPrecedentDate = LocalDate.of(anneeFiltre, moisFiltre, 1).minusMonths(1);
        double totalMoisPrecedent = toutesLesDepenses.stream()
            .filter(d -> d.getDateDepense() != null
                && d.getDateDepense().getMonthValue() == moisPrecedentDate.getMonthValue()
                && d.getDateDepense().getYear() == moisPrecedentDate.getYear())
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();

        Map<String, Double> parCategorie = depensesDuMois.stream()
            .collect(Collectors.groupingBy(
                d -> d.getCategorie() != null ? d.getCategorie() : "AUTRE",
                LinkedHashMap::new,
                Collectors.summingDouble(d -> d.getMontant() != null ? d.getMontant() : 0)));

        List<Map<String, Object>> repartition = new java.util.ArrayList<>();
        double cumul = 0;
        List<Map.Entry<String, Double>> triees = parCategorie.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        for (Map.Entry<String, Double> e : triees) {
            double pourcentage = totalDuMois > 0 ? Math.round(e.getValue() / totalDuMois * 1000) / 10.0 : 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("categorie", e.getKey());
            row.put("montant", e.getValue());
            row.put("pourcentage", pourcentage);
            row.put("offsetCumule", -cumul);
            repartition.add(row);
            cumul += pourcentage;
        }

        String categorieMajeure = triees.isEmpty() ? null : triees.get(0).getKey();
        double pourcentageCategorieMajeure = totalDuMois > 0 && !triees.isEmpty()
            ? Math.round(triees.get(0).getValue() / totalDuMois * 1000) / 10.0 : 0;

        Depense plusGrosseDepense = depensesDuMois.stream()
            .max(Comparator.comparing(d -> d.getMontant() != null ? d.getMontant() : 0))
            .orElse(null);

        String anneeScolaireCourante = moisFiltre >= 9
            ? anneeFiltre + "-" + (anneeFiltre + 1)
            : (anneeFiltre - 1) + "-" + anneeFiltre;
        List<LigneBudget> lignesDepense = budgetRepository
            .findByEtablissementIdAndAnneeScolaireOrderByCategorieAscDesignationAsc(etabId, anneeScolaireCourante)
            .stream().filter(l -> "DEPENSE".equals(l.getCategorie())).collect(Collectors.toList());
        double budgetAnnuelPrevu = lignesDepense.stream()
            .mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double depenseAnnuelleTotale = toutesLesDepenses.stream()
            .filter(d -> anneeScolaireCourante.equals(d.getAnneeScolaire()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double budgetRestant = budgetAnnuelPrevu - depenseAnnuelleTotale;
        double tauxBudgetUtilise = budgetAnnuelPrevu > 0 ? Math.round(depenseAnnuelleTotale / budgetAnnuelPrevu * 1000) / 10.0 : 0;

        Etablissement etab = etablissementService.getCurrentEtablissement();

        model.addAttribute("nomsMois", List.of("Janvier","Fevrier","Mars","Avril","Mai","Juin",
            "Juillet","Aout","Septembre","Octobre","Novembre","Decembre"));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("depenses", depensesFiltrees);
        model.addAttribute("moisFiltre", moisFiltre);
        model.addAttribute("anneeFiltre", anneeFiltre);
        model.addAttribute("categorieFiltre", categorie);
        model.addAttribute("statutFiltre", statut);
        model.addAttribute("nomMoisFiltre", LocalDate.of(anneeFiltre, moisFiltre, 1).getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH));
        model.addAttribute("totalDuMois", totalDuMois);
        model.addAttribute("variationVsMoisPrecedent", totalDuMois - totalMoisPrecedent);
        model.addAttribute("categorieMajeure", categorieMajeure);
        model.addAttribute("pourcentageCategorieMajeure", pourcentageCategorieMajeure);
        model.addAttribute("budgetRestant", budgetRestant);
        model.addAttribute("budgetAnnuelPrevu", budgetAnnuelPrevu);
        model.addAttribute("depenseAnnuelleTotale", depenseAnnuelleTotale);
        model.addAttribute("tauxBudgetUtilise", tauxBudgetUtilise);
        model.addAttribute("repartition", repartition);
        model.addAttribute("plusGrosseDepense", plusGrosseDepense);
        model.addAttribute("etablissement", etab);

        return "depenses";
    }

    @GetMapping("/detail")
    public String detail(
            @RequestParam(required = false, defaultValue = "MOIS") String periode,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee,
            Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        LocalDate aujourdHui = LocalDate.now();
        int moisFiltre = mois != null ? mois : aujourdHui.getMonthValue();
        int anneeFiltre = annee != null ? annee : aujourdHui.getYear();

        LocalDate ancre = LocalDate.of(anneeFiltre, moisFiltre, 1);
        LocalDate debutPeriode;
        LocalDate finPeriode;
        LocalDate debutPeriodePrecedente;
        LocalDate finPeriodePrecedente;

        switch (periode) {
            case "TRIMESTRE" -> {
                int debutTrimestreMois = ((moisFiltre - 1) / 3) * 3 + 1;
                debutPeriode = LocalDate.of(anneeFiltre, debutTrimestreMois, 1);
                finPeriode = debutPeriode.plusMonths(3).minusDays(1);
                debutPeriodePrecedente = debutPeriode.minusMonths(3);
                finPeriodePrecedente = debutPeriode.minusDays(1);
            }
            case "ANNEE" -> {
                debutPeriode = LocalDate.of(anneeFiltre, 1, 1);
                finPeriode = LocalDate.of(anneeFiltre, 12, 31);
                debutPeriodePrecedente = debutPeriode.minusYears(1);
                finPeriodePrecedente = finPeriode.minusYears(1);
            }
            default -> {
                debutPeriode = ancre.withDayOfMonth(1);
                finPeriode = ancre.withDayOfMonth(ancre.lengthOfMonth());
                debutPeriodePrecedente = debutPeriode.minusMonths(1);
                finPeriodePrecedente = debutPeriodePrecedente.withDayOfMonth(debutPeriodePrecedente.lengthOfMonth());
            }
        }

        List<Depense> toutesLesDepenses = depenseRepository.findByEtablissementIdOrderByDateDepenseDesc(etabId);

        List<Depense> depensesPeriode = toutesLesDepenses.stream()
            .filter(d -> d.getDateDepense() != null
                && !d.getDateDepense().isBefore(debutPeriode) && !d.getDateDepense().isAfter(finPeriode))
            .collect(Collectors.toList());
        List<Depense> depensesPeriodePrecedente = toutesLesDepenses.stream()
            .filter(d -> d.getDateDepense() != null
                && !d.getDateDepense().isBefore(debutPeriodePrecedente) && !d.getDateDepense().isAfter(finPeriodePrecedente))
            .collect(Collectors.toList());

        double totalPeriode = depensesPeriode.stream().mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double totalPeriodePrecedente = depensesPeriodePrecedente.stream().mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();

        Map<String, Double> parCategorie = depensesPeriode.stream()
            .collect(Collectors.groupingBy(
                d -> d.getCategorie() != null ? d.getCategorie() : "AUTRE",
                LinkedHashMap::new,
                Collectors.summingDouble(d -> d.getMontant() != null ? d.getMontant() : 0)));
        double maxCategorie = parCategorie.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        List<Map<String, Object>> repartition = parCategorie.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(e -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("categorie", e.getKey());
                row.put("montant", e.getValue());
                row.put("pourcentageBarre", maxCategorie > 0 ? Math.round(e.getValue() / maxCategorie * 1000) / 10.0 : 0);
                return row;
            })
            .collect(Collectors.toList());

        Map<String, Double> parFournisseur = depensesPeriode.stream()
            .filter(d -> d.getBeneficiaire() != null && !d.getBeneficiaire().isBlank())
            .collect(Collectors.groupingBy(Depense::getBeneficiaire, LinkedHashMap::new,
                Collectors.summingDouble(d -> d.getMontant() != null ? d.getMontant() : 0)));
        List<Map<String, Object>> principauxFournisseurs = parFournisseur.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(4)
            .map(e -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("nom", e.getKey());
                row.put("montant", e.getValue());
                String cat = depensesPeriode.stream()
                    .filter(d -> e.getKey().equals(d.getBeneficiaire()))
                    .map(Depense::getCategorie).findFirst().orElse("");
                row.put("categorie", cat);
                String[] mots = e.getKey().trim().split("\\s+");
                String initiales = mots.length > 1
                    ? ("" + mots[0].charAt(0) + mots[1].charAt(0)).toUpperCase()
                    : e.getKey().substring(0, Math.min(2, e.getKey().length())).toUpperCase();
                row.put("initiales", initiales);
                return row;
            })
            .collect(Collectors.toList());

        String anneeScolairePeriode = ancre.getMonthValue() >= 9
            ? anneeFiltre + "-" + (anneeFiltre + 1)
            : (anneeFiltre - 1) + "-" + anneeFiltre;
        List<LigneBudget> lignesDepense = budgetRepository
            .findByEtablissementIdAndAnneeScolaireOrderByCategorieAscDesignationAsc(etabId, anneeScolairePeriode)
            .stream().filter(l -> "DEPENSE".equals(l.getCategorie())).collect(Collectors.toList());
        double budgetAnnuelPrevu = lignesDepense.stream()
            .mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double depenseAnnuelleTotale = toutesLesDepenses.stream()
            .filter(d -> anneeScolairePeriode.equals(d.getAnneeScolaire()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double tauxBudgetUtilise = budgetAnnuelPrevu > 0 ? Math.round(depenseAnnuelleTotale / budgetAnnuelPrevu * 1000) / 10.0 : 0;

        // "Efficacite Energie/Eau" : variation reelle du poste Services Publics vs la periode precedente
        double servicesPublicsPeriode = parCategorie.getOrDefault("SERVICES_PUBLICS", 0.0);
        double servicesPublicsPeriodePrecedente = depensesPeriodePrecedente.stream()
            .filter(d -> "SERVICES_PUBLICS".equals(d.getCategorie()))
            .mapToDouble(d -> d.getMontant() != null ? d.getMontant() : 0).sum();
        double variationServicesPublics = servicesPublicsPeriode - servicesPublicsPeriodePrecedente;

        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("periode", periode);
        model.addAttribute("moisFiltre", moisFiltre);
        model.addAttribute("anneeFiltre", anneeFiltre);
        model.addAttribute("debutPeriode", debutPeriode);
        model.addAttribute("finPeriode", finPeriode);
        model.addAttribute("totalPeriode", totalPeriode);
        model.addAttribute("variationVsPeriodePrecedente", totalPeriode - totalPeriodePrecedente);
        model.addAttribute("tauxBudgetUtilise", tauxBudgetUtilise);
        model.addAttribute("budgetAnnuelPrevu", budgetAnnuelPrevu);
        model.addAttribute("variationServicesPublics", variationServicesPublics);
        model.addAttribute("repartition", repartition);
        model.addAttribute("principauxFournisseurs", principauxFournisseurs);
        model.addAttribute("depensesPeriode", depensesPeriode.stream()
            .sorted(Comparator.comparing(Depense::getDateDepense).reversed()).collect(Collectors.toList()));

        return "depenses-detail";
    }

    @PostMapping
    public String ajouterDepense(
            @RequestParam String designation,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) String beneficiaire,
            @RequestParam Double montant,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDepense,
            @RequestParam(defaultValue = "PAYE") String statut,
            @RequestParam(required = false) MultipartFile justificatif,
            RedirectAttributes ra) throws IOException {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Depense d = new Depense();
        d.setDesignation(designation);
        d.setCategorie(categorie);
        d.setBeneficiaire(beneficiaire);
        d.setMontant(montant);
        d.setDateDepense(dateDepense);
        d.setStatut(statut);
        d.setAnneeScolaire(dateDepense.getMonthValue() >= 9
            ? dateDepense.getYear() + "-" + (dateDepense.getYear() + 1)
            : (dateDepense.getYear() - 1) + "-" + dateDepense.getYear());
        d.setEtablissementId(etabId);
        if (justificatif != null && !justificatif.isEmpty()) {
            String chemin = fileStorageService.store(justificatif, "depenses");
            d.setJustificatifPath(chemin);
            d.setJustificatifNomOriginal(justificatif.getOriginalFilename());
        }
        depenseRepository.save(d);

        journalService.log("DEPENSE_AJOUTÉE", "FINANCES", designation + " — " + montant + " F (" + beneficiaire + ")");
        ra.addFlashAttribute("successMsg", "Depense enregistree : " + designation + ".");
        return "redirect:/finances/depenses?mois=" + dateDepense.getMonthValue() + "&annee=" + dateDepense.getYear();
    }

    @PostMapping("/{id}/supprimer")
    public String supprimerDepense(@PathVariable Long id, RedirectAttributes ra) {
        depenseRepository.findById(id).ifPresent(d -> {
            journalService.log("DEPENSE_SUPPRIMÉE", "FINANCES", d.getDesignation() + " — " + d.getMontant() + " F");
            if (d.getJustificatifPath() != null) fileStorageService.delete(d.getJustificatifPath());
            depenseRepository.delete(d);
        });
        ra.addFlashAttribute("successMsg", "Depense supprimee.");
        return "redirect:/finances/depenses";
    }
}
