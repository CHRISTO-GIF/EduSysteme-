package holyflame.administration.controller;

import holyflame.administration.model.Eleve;
import holyflame.administration.model.FraisScolarite;
import holyflame.administration.model.LigneBudget;
import holyflame.administration.model.Paiement;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.FraisScolariteRepository;
import holyflame.administration.repository.LigneBudgetRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.EmailService;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.service.JournalService;
import holyflame.administration.service.NombreEnLettresService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/finances")
public class FinancesController {

    @Autowired private PaiementRepository paiementRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private LigneBudgetRepository budgetRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private FraisScolariteRepository fraisScolariteRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private JournalService journalService;
    @Autowired private EmailService emailService;
    @Autowired private NombreEnLettresService nombreEnLettresService;

    @GetMapping
    public String index(@RequestParam(defaultValue = "2025-2026") String annee, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Paiement> paiements = paiementRepository.findByEtablissementId(etabId);
        double totalEncaisse = paiements.stream()
            .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();

        List<LigneBudget> lignes = budgetRepository.findByEtablissementIdAndAnneeScolaireOrderByCategorieAscDesignationAsc(etabId, annee);
        double totalRevenuPrevu = lignes.stream().filter(l -> "REVENU".equals(l.getCategorie()))
            .mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double totalDepensePrevu = lignes.stream().filter(l -> "DEPENSE".equals(l.getCategorie()))
            .mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double totalRevenuReel = lignes.stream().filter(l -> "REVENU".equals(l.getCategorie()))
            .mapToDouble(l -> l.getMontantReel() != null ? l.getMontantReel() : 0).sum();
        double totalDepenseReel = lignes.stream().filter(l -> "DEPENSE".equals(l.getCategorie()))
            .mapToDouble(l -> l.getMontantReel() != null ? l.getMontantReel() : 0).sum();

        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("paiements",       paiements);
        model.addAttribute("eleves",          eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId));
        model.addAttribute("tousLesFrais",    fraisScolariteRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId));
        model.addAttribute("totalEncaisse",   totalEncaisse);
        model.addAttribute("lignes",          lignes);
        model.addAttribute("annee",           annee);
        model.addAttribute("totalRevenuPrevu",  totalRevenuPrevu);
        model.addAttribute("totalDepensePrevu", totalDepensePrevu);
        model.addAttribute("totalRevenuReel",   totalRevenuReel);
        model.addAttribute("totalDepenseReel",  totalDepenseReel);
        model.addAttribute("soldePrevu",      totalRevenuPrevu - totalDepensePrevu);
        model.addAttribute("soldeReel",       totalRevenuReel  - totalDepenseReel);
        model.addAttribute("tauxEncaissement", totalRevenuPrevu > 0 ? totalEncaisse / totalRevenuPrevu * 100 : 0);

        // Paiements en attente (reste a percevoir vs budget prevu)
        double paiementsEnAttente = Math.max(totalRevenuPrevu - totalEncaisse, 0);
        model.addAttribute("paiementsEnAttente", paiementsEnAttente);

        // Depenses du mois en cours
        int moisCourant = LocalDate.now().getMonthValue();
        double depensesDuMois = lignes.stream()
            .filter(l -> "DEPENSE".equals(l.getCategorie()) && moisCourant == (l.getMois() != null ? l.getMois() : -1))
            .mapToDouble(l -> l.getMontantReel() != null ? l.getMontantReel() : 0).sum();
        model.addAttribute("depensesDuMois", depensesDuMois);

        // Evolution mensuelle (revenus vs depenses reels), uniquement les mois avec des donnees
        Map<Integer, double[]> parMois = new LinkedHashMap<>();
        for (LigneBudget l : lignes) {
            if (l.getMois() == null) continue;
            double[] paire = parMois.computeIfAbsent(l.getMois(), m -> new double[2]);
            double montant = l.getMontantReel() != null ? l.getMontantReel() : 0;
            if ("REVENU".equals(l.getCategorie())) paire[0] += montant;
            else if ("DEPENSE".equals(l.getCategorie())) paire[1] += montant;
        }
        List<Map<String, Object>> evolution = new ArrayList<>();
        String[] nomsMois = {"Jan","Fev","Mar","Avr","Mai","Juin","Juil","Aout","Sep","Oct","Nov","Dec"};
        parMois.keySet().stream().sorted().forEach(m -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("mois", nomsMois[m - 1]);
            point.put("revenu", parMois.get(m)[0]);
            point.put("depense", parMois.get(m)[1]);
            evolution.add(point);
        });
        model.addAttribute("evolution", evolution);
        double maxEvolution = evolution.stream()
            .flatMapToDouble(p -> java.util.stream.DoubleStream.of((double) p.get("revenu"), (double) p.get("depense")))
            .max().orElse(1);
        model.addAttribute("maxEvolution", maxEvolution > 0 ? maxEvolution : 1);

        // Repartition des revenus reels par type de ligne
        Map<String, Double> revenusParType = lignes.stream()
            .filter(l -> "REVENU".equals(l.getCategorie()))
            .collect(Collectors.groupingBy(
                l -> l.getTypeLigne() != null ? l.getTypeLigne() : "AUTRE",
                LinkedHashMap::new,
                Collectors.summingDouble(l -> l.getMontantReel() != null ? l.getMontantReel() : 0)));
        double totalRevenusReelsPositifs = revenusParType.values().stream().mapToDouble(Double::doubleValue).sum();
        List<Map<String, Object>> repartitionRevenus = new ArrayList<>();
        double cumul = 0;
        List<Map.Entry<String, Double>> triees = revenusParType.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        for (Map.Entry<String, Double> e : triees) {
            double pourcentage = totalRevenusReelsPositifs > 0 ? Math.round(e.getValue() / totalRevenusReelsPositifs * 1000) / 10.0 : 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", e.getKey());
            row.put("montant", e.getValue());
            row.put("pourcentage", pourcentage);
            row.put("offsetCumule", -cumul);
            repartitionRevenus.add(row);
            cumul += pourcentage;
        }
        model.addAttribute("repartitionRevenus", repartitionRevenus);

        // Dernieres transactions (paiements reels, plus recents en premier)
        List<Paiement> dernieresTransactions = paiements.stream()
            .sorted(Comparator.comparing(Paiement::getDatePaiement, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(8)
            .collect(Collectors.toList());
        model.addAttribute("dernieresTransactions", dernieresTransactions);

        // Statut des scolarites : eleves a jour vs en attente, base sur les frais obligatoires reels
        List<Eleve> tousLesEleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        List<FraisScolarite> fraisObligatoires = fraisScolariteRepository
            .findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId).stream()
            .filter(FraisScolarite::isObligatoire)
            .collect(Collectors.toList());
        Map<Long, Double> paiementsParEleve = paiements.stream()
            .filter(p -> p.getEleve() != null)
            .collect(Collectors.groupingBy(p -> p.getEleve().getId(),
                Collectors.summingDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0)));

        int nbPayes = 0;
        int nbEnAttente = 0;
        for (Eleve e : tousLesEleves) {
            double du = fraisObligatoires.stream()
                .filter(f -> f.getNiveauCible() == null || f.getNiveauCible().isBlank()
                    || (e.getClasse() != null && f.getNiveauCible().equalsIgnoreCase(e.getClasse().getNiveau())))
                .mapToDouble(f -> f.getMontant() != null ? f.getMontant() : 0).sum();
            double verse = paiementsParEleve.getOrDefault(e.getId(), 0.0);
            if (du <= 0 || verse >= du) nbPayes++; else nbEnAttente++;
        }
        int totalEleves = nbPayes + nbEnAttente;
        model.addAttribute("nbElevesPayes", nbPayes);
        model.addAttribute("nbElevesEnAttente", nbEnAttente);
        model.addAttribute("tauxScolaritesPayees", totalEleves > 0 ? Math.round(nbPayes * 1000.0 / totalEleves) / 10.0 : 0);

        return "finances";
    }

    @GetMapping("/nouvelle-transaction")
    public String nouvelleTransaction(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Paiement> paiements = paiementRepository.findByEtablissementId(etabId);
        double totalEncaisse = paiements.stream()
            .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();

        String anneeCourante = "2025-2026";
        List<LigneBudget> lignes = budgetRepository.findByEtablissementIdAndAnneeScolaireOrderByCategorieAscDesignationAsc(etabId, anneeCourante);
        double totalRevenuPrevu = lignes.stream().filter(l -> "REVENU".equals(l.getCategorie()))
            .mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double paiementsEnAttente = Math.max(totalRevenuPrevu - totalEncaisse, 0);

        List<Paiement> dernieresTransactions = paiements.stream()
            .sorted(Comparator.comparing(Paiement::getDatePaiement, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(4)
            .collect(Collectors.toList());

        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("eleves", eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId));
        model.addAttribute("tousLesFrais", fraisScolariteRepository.findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId));
        model.addAttribute("totalEncaisse", totalEncaisse);
        model.addAttribute("paiementsEnAttente", paiementsEnAttente);
        model.addAttribute("dernieresTransactions", dernieresTransactions);
        return "finances-nouvelle-transaction";
    }

    @PostMapping("/rappels")
    public String envoyerRappels(RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Eleve> tousLesEleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        List<Paiement> paiements = paiementRepository.findByEtablissementId(etabId);
        List<FraisScolarite> fraisObligatoires = fraisScolariteRepository
            .findByEtablissementIdOrderByTypeFraisAscDesignationAsc(etabId).stream()
            .filter(FraisScolarite::isObligatoire)
            .collect(Collectors.toList());
        Map<Long, Double> paiementsParEleve = paiements.stream()
            .filter(p -> p.getEleve() != null)
            .collect(Collectors.groupingBy(p -> p.getEleve().getId(),
                Collectors.summingDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0)));

        int nbRappels = 0;
        for (Eleve e : tousLesEleves) {
            double du = fraisObligatoires.stream()
                .filter(f -> f.getNiveauCible() == null || f.getNiveauCible().isBlank()
                    || (e.getClasse() != null && f.getNiveauCible().equalsIgnoreCase(e.getClasse().getNiveau())))
                .mapToDouble(f -> f.getMontant() != null ? f.getMontant() : 0).sum();
            double verse = paiementsParEleve.getOrDefault(e.getId(), 0.0);
            if (du > 0 && verse < du) {
                e.setDernierRappelPaiement(LocalDateTime.now());
                eleveRepository.save(e);
                nbRappels++;
            }
        }
        journalService.log("RAPPELS_PAIEMENT_ENVOYÉS", "FINANCES", nbRappels + " eleve(s) marque(s) comme rappele(s).");
        ra.addFlashAttribute("successMsg",
            "Rappel enregistre pour " + nbRappels + " eleve(s) en attente de paiement. "
            + "(Aucun email n'est envoye : la configuration SMTP n'est pas encore en place.)");
        return "redirect:/finances";
    }

    // ===== PAIEMENTS =====
    @PostMapping("/paiements")
    public String enregistrerPaiement(
            @RequestParam Long eleveId,
            @RequestParam Double montantVerse,
            @RequestParam String typePaiement,
            @RequestParam String modePaiement,
            @RequestParam(required = false) String recuNumero,
            @RequestParam(required = false) String fraisScolariteId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate datePaiement,
            @RequestParam(required = false) String description,
            RedirectAttributes ra) {

        Long etabIdCourant = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        if (etabIdCourant == null || !etabIdCourant.equals(eleve.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Élève introuvable dans cet établissement.");
        }
        if (montantVerse == null || montantVerse <= 0) {
            ra.addFlashAttribute("erreur", "Le montant versé doit être supérieur à 0.");
            return "redirect:/finances?tab=paiements";
        }
        Paiement p = new Paiement();
        p.setEleve(eleve); p.setMontantVerse(montantVerse);
        p.setTypePaiement(typePaiement); p.setModePaiement(modePaiement);
        p.setDatePaiement(datePaiement != null ? datePaiement.atStartOfDay() : LocalDateTime.now());
        p.setDescription(description);
        p.setRecuNumero(recuNumero != null && !recuNumero.isBlank() ? recuNumero : "HF-" + System.currentTimeMillis());
        if (fraisScolariteId != null && !fraisScolariteId.isBlank()) {
            fraisScolariteRepository.findById(Long.parseLong(fraisScolariteId)).ifPresent(p::setFraisScolarite);
        }
        var utilisateur = etablissementService.getCurrentUtilisateur();
        if (utilisateur != null) p.setEnregistreParId(utilisateur.getId());
        paiementRepository.save(p);
        journalService.log("PAIEMENT_ENREGISTRÉ", "FINANCES",
            eleve.getNom() + " " + eleve.getPrenom() + " — " + montantVerse + " F (" + typePaiement + ")");

        boolean envoye = envoyerRecuParEmail(p);
        String emailDestinataire = adresseParent(eleve);
        if (envoye) {
            ra.addFlashAttribute("successMsg", "Paiement enregistre. Une copie du recu a ete envoyee par email a " + emailDestinataire + ".");
        } else if (emailDestinataire != null) {
            ra.addFlashAttribute("erreurEmail", "Paiement enregistre, mais l'envoi du recu par email a echoue.");
        }
        return "redirect:/finances/paiements/" + p.getId() + "/recu";
    }

    @GetMapping("/paiements/{id}/recu")
    public String recu(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Paiement p = paiementRepository.findById(id).orElse(null);
        if (p == null || p.getEleve() == null || !etabId.equals(p.getEleve().getEtablissementId())) {
            ra.addFlashAttribute("erreur", "Paiement introuvable.");
            return "redirect:/finances?tab=paiements";
        }

        Map<String, String> params = parametreRepository.findByEtablissementId(etabId).stream()
            .collect(Collectors.toMap(
                holyflame.administration.model.Parametre::getCle,
                holyflame.administration.model.Parametre::getValeur,
                (a, b) -> a));

        holyflame.administration.model.Etablissement etab = etablissementService.getCurrentEtablissement();
        String monnaie = params.getOrDefault("MONNAIE", "FCFA");
        long montantEntier = p.getMontantVerse() != null ? Math.round(p.getMontantVerse()) : 0;

        model.addAttribute("paiement",   p);
        model.addAttribute("nomEtab",    etab != null && etab.getNom() != null && !etab.getNom().isBlank() ? etab.getNom() : "HolyFlame");
        model.addAttribute("adresseEtab",etab != null && etab.getAdresse() != null ? etab.getAdresse() : "");
        model.addAttribute("telEtab",    params.getOrDefault("TELEPHONE_ECOLE", ""));
        model.addAttribute("anneeScolaire", params.getOrDefault("ANNEE_SCOLAIRE", "2025-2026"));
        model.addAttribute("logoPath",   params.getOrDefault("LOGO_ETAB", null));
        model.addAttribute("devise",     params.getOrDefault("DEVISE", ""));
        model.addAttribute("emailEtab",  params.getOrDefault("EMAIL_ECOLE", ""));
        model.addAttribute("monnaie",    monnaie);
        model.addAttribute("montantEnLettres", nombreEnLettresService.convertir(montantEntier, monnaie));
        model.addAttribute("modePaiementLabel", libelleModePaiement(p.getModePaiement()));
        model.addAttribute("chefEtablissement", params.get("CONTACT_PRINCIPAL"));
        model.addAttribute("enregistrePar", p.getEnregistreParId() != null
            ? utilisateurRepository.findById(p.getEnregistreParId()).orElse(null) : null);
        model.addAttribute("emailDestinataire", adresseParent(p.getEleve()));
        return "recu-paiement";
    }

    @PostMapping("/paiements/{id}/renvoyer-email")
    public String renvoyerEmail(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Paiement p = paiementRepository.findById(id).orElse(null);
        if (p == null || p.getEleve() == null || !etabId.equals(p.getEleve().getEtablissementId())) {
            ra.addFlashAttribute("erreur", "Paiement introuvable.");
            return "redirect:/finances?tab=paiements";
        }
        String emailDestinataire = adresseParent(p.getEleve());
        boolean envoye = envoyerRecuParEmail(p);
        if (envoye) {
            ra.addFlashAttribute("successMsg", "Recu renvoye par email a " + emailDestinataire + ".");
        } else {
            ra.addFlashAttribute("erreurEmail", "Impossible d'envoyer le recu par email (aucune adresse parent valide ou service email indisponible).");
        }
        return "redirect:/finances/paiements/" + id + "/recu";
    }

    private String adresseParent(Eleve eleve) {
        if (eleve.getPereEmail() != null && !eleve.getPereEmail().isBlank()) return eleve.getPereEmail();
        if (eleve.getMereEmail() != null && !eleve.getMereEmail().isBlank()) return eleve.getMereEmail();
        return eleve.getEmailParent();
    }

    private boolean envoyerRecuParEmail(Paiement p) {
        String destinataire = adresseParent(p.getEleve());
        if (destinataire == null || destinataire.isBlank()) return false;
        String corps = "<p>Bonjour,</p>"
            + "<p>Nous confirmons la reception d'un paiement pour <strong>" + p.getEleve().getPrenom() + " " + p.getEleve().getNom() + "</strong>.</p>"
            + "<table style=\"border-collapse:collapse;margin:16px 0;\">"
            + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Reçu n°</td><td><strong>" + p.getRecuNumero() + "</strong></td></tr>"
            + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Type</td><td>" + (p.getFraisScolarite() != null ? p.getFraisScolarite().getDesignation() : p.getTypePaiement()) + "</td></tr>"
            + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Date</td><td>" + p.getDatePaiement().toLocalDate() + "</td></tr>"
            + "<tr><td style=\"padding:4px 12px 4px 0;color:#555;\">Montant versé</td><td><strong>" + Math.round(p.getMontantVerse()) + " F</strong></td></tr>"
            + "</table>"
            + "<p>Merci de conserver cet email comme justificatif. Pour toute question, contactez le secrétariat.</p>";
        return emailService.envoyer(destinataire, "Reçu de paiement " + p.getRecuNumero(), corps);
    }

    private String libelleModePaiement(String mode) {
        if (mode == null) return "--";
        return switch (mode) {
            case "ESPECES" -> "Espèces";
            case "VIREMENT" -> "Virement bancaire";
            case "MOBILE_MONEY" -> "Mobile Money";
            case "CHEQUE" -> "Chèque";
            default -> mode;
        };
    }

    @PostMapping("/paiements/{id}/supprimer")
    public String supprimerPaiement(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        paiementRepository.findById(id)
            .filter(p -> p.getEleve() != null && etabId.equals(p.getEleve().getEtablissementId()))
            .ifPresent(p -> {
                journalService.log("PAIEMENT_SUPPRIMÉ", "FINANCES",
                    "Reçu " + (p.getRecuNumero() != null ? p.getRecuNumero() : id));
                paiementRepository.deleteById(id);
            });
        return "redirect:/finances?tab=paiements";
    }

    // ===== BUDGET =====
    @PostMapping("/budget")
    public String ajouterBudget(
            @RequestParam String designation,
            @RequestParam String categorie,
            @RequestParam String typeLigne,
            @RequestParam Double montantPrevu,
            @RequestParam(required = false) Double montantReel,
            @RequestParam(required = false) Integer mois,
            @RequestParam(defaultValue = "2025-2026") String anneeScolaire,
            @RequestParam(required = false) String notes) {

        LigneBudget l = new LigneBudget();
        l.setDesignation(designation); l.setCategorie(categorie); l.setTypeLigne(typeLigne);
        l.setMontantPrevu(montantPrevu); l.setMontantReel(montantReel); l.setMois(mois);
        l.setAnneeScolaire(anneeScolaire); l.setNotes(notes); l.setDateCreation(LocalDate.now());
        l.setEtablissementId(etablissementService.getCurrentEtablissementId());
        budgetRepository.save(l);
        return "redirect:/finances?tab=budget&annee=" + anneeScolaire;
    }

    @PostMapping("/budget/{id}/supprimer")
    public String supprimerBudget(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        LigneBudget ligne = budgetRepository.findById(id).orElse(null);
        String annee = ligne != null ? ligne.getAnneeScolaire() : "2025-2026";
        if (ligne != null && etabId.equals(ligne.getEtablissementId())) {
            budgetRepository.deleteById(id);
        }
        return "redirect:/finances?tab=budget&annee=" + annee;
    }

    @PostMapping("/budget/{id}/realiser")
    public String realiserBudget(@PathVariable Long id, @RequestParam Double montantReel) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        budgetRepository.findById(id)
            .filter(l -> etabId.equals(l.getEtablissementId()))
            .ifPresent(l -> { l.setMontantReel(montantReel); budgetRepository.save(l); });
        return "redirect:/finances?tab=budget";
    }
}
