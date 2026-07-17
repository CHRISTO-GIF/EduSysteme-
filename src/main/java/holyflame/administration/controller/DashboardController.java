package holyflame.administration.controller;

import holyflame.administration.model.Note;
import holyflame.administration.model.Paiement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.*;
import holyflame.administration.service.AlerteService;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private LigneBudgetRepository budgetRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private AlerteService alerteService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();

        Utilisateur utilisateurConnecte = etablissementService.getCurrentUtilisateur();
        model.addAttribute("utilisateurConnecte", utilisateurConnecte);
        model.addAttribute("nomEtablissement",
            etablissementService.getCurrentEtablissement() != null
                ? etablissementService.getCurrentEtablissement().getNom() : "EduSystem Pro");

        // Alertes automatiques
        model.addAttribute("alertes", alerteService.getAlertes(etabId));

        // KPIs — filtrés par établissement
        model.addAttribute("totalEleves",     etabId != null ? eleveRepository.countByEtablissementId(etabId)     : eleveRepository.count());
        model.addAttribute("totalClasses",    etabId != null ? classeRepository.findByEtablissementId(etabId).size() : classeRepository.count());
        model.addAttribute("totalAbsences",   etabId != null ? absenceRepository.countByEtablissementId(etabId)   : absenceRepository.count());
        model.addAttribute("totalPersonnels", etabId != null ? personnelRepository.countByEtablissementId(etabId) : personnelRepository.count());

        // Finances — filtrées par établissement
        List<Paiement> paiements = etabId != null
            ? paiementRepository.findByEtablissementId(etabId)
            : paiementRepository.findAll();
        double totalEncaisse = paiements.stream()
            .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();
        model.addAttribute("totalEncaisse", totalEncaisse);

        // Budget — filtré par établissement
        String anneeScolaire = etabId != null
            ? parametreRepository.findByCleAndEtablissementId("ANNEE_SCOLAIRE", etabId)
                .map(p -> p.getValeur()).orElse("2025-2026")
            : "2025-2026";
        var lignesBudget = etabId != null
            ? budgetRepository.findByEtablissementIdAndAnneeScolaireOrderByCategorieAscDesignationAsc(etabId, anneeScolaire)
            : budgetRepository.findByAnneeScolaireOrderByCategorieAscDesignationAsc(anneeScolaire);
        double budgetRevenu = lignesBudget.stream().filter(l -> "REVENU".equals(l.getCategorie()))
            .mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        double budgetDepense = lignesBudget.stream().filter(l -> "DEPENSE".equals(l.getCategorie()))
            .mapToDouble(l -> l.getMontantPrevu() != null ? l.getMontantPrevu() : 0).sum();
        model.addAttribute("budgetRevenu",  budgetRevenu);
        model.addAttribute("budgetDepense", budgetDepense);
        model.addAttribute("anneeScolaire", anneeScolaire);

        // Graphique paiements par mois
        String[] moisNoms = {"Jan","Fév","Mar","Avr","Mai","Jun","Jul","Aoû","Sep","Oct","Nov","Déc"};
        int annee = LocalDateTime.now().getYear();
        Map<Integer, Double> pParMois = paiements.stream()
            .filter(p -> p.getDatePaiement() != null && p.getDatePaiement().getYear() == annee)
            .collect(Collectors.groupingBy(p -> p.getDatePaiement().getMonthValue(),
                Collectors.summingDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0)));
        List<String> pLabels = new ArrayList<>();
        List<Double> pData   = new ArrayList<>();
        for (int i = 1; i <= 12; i++) { pLabels.add(moisNoms[i-1]); pData.add(pParMois.getOrDefault(i, 0.0)); }
        model.addAttribute("paiementsLabels", pLabels);
        model.addAttribute("paiementsData",   pData);
        double maxPaiementMois = pData.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        model.addAttribute("maxPaiementMois", Math.max(1.0, maxPaiementMois));
        int moisCourantIndex = LocalDateTime.now().getMonthValue() - 1;
        model.addAttribute("moisCourantIndex", moisCourantIndex);
        model.addAttribute("totalEncaisseMois", pData.get(moisCourantIndex));

        // Graphique absences par mois — filtrées par établissement
        List<holyflame.administration.model.Absence> absences = etabId != null
            ? absenceRepository.findByEtablissementId(etabId)
            : absenceRepository.findAll();
        Map<Integer, Long> absParMois = absences.stream()
            .filter(a -> a.getDate() != null && a.getDate().getYear() == annee)
            .collect(Collectors.groupingBy(a -> a.getDate().getMonthValue(), Collectors.counting()));
        List<Long> aData = new ArrayList<>();
        for (int i = 1; i <= 12; i++) aData.add(absParMois.getOrDefault(i, 0L));
        model.addAttribute("absencesData", aData);
        model.addAttribute("maxAbsences", Math.max(1, aData.stream().mapToLong(Long::longValue).max().orElse(1)));

        // Graphique notes par mention — filtrées par établissement
        List<Note> toutesNotes = etabId != null
            ? noteRepository.findByEtablissementId(etabId)
            : noteRepository.findAllByOrderByDateEvaluationDesc();
        List<Long> notesMentions = List.of(
            toutesNotes.stream().filter(n -> n.getValeur() != null && n.getValeur() >= 16).count(),
            toutesNotes.stream().filter(n -> n.getValeur() != null && n.getValeur() >= 14 && n.getValeur() < 16).count(),
            toutesNotes.stream().filter(n -> n.getValeur() != null && n.getValeur() >= 12 && n.getValeur() < 14).count(),
            toutesNotes.stream().filter(n -> n.getValeur() != null && n.getValeur() >= 10 && n.getValeur() < 12).count(),
            toutesNotes.stream().filter(n -> n.getValeur() != null && n.getValeur() < 10).count()
        );
        model.addAttribute("notesMentions", notesMentions);
        model.addAttribute("totalNotes", Math.max(1, notesMentions.stream().mapToLong(Long::longValue).sum()));

        return "dashboard";
    }

    @GetMapping("/login")
    public String login() { return "login"; }
}
