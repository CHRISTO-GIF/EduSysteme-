package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.EtablissementService;
import holyflame.administration.util.AnneeScolaireUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/rh")
public class RHController {

    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private ContratRepository contratRepository;
    @Autowired private CongeRepository congeRepository;
    @Autowired private SalaireMensuelRepository salaireRepository;
    @Autowired private LigneSalaireRepository ligneSalaireRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private CategorieComptableRepository categorieComptableRepository;
    @Autowired private DepenseRepository depenseRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.AnneeScolaireService anneeScolaireService;

    @GetMapping
    public String index() {
        return "redirect:/personnel";
    }

    private Personnel personnelDuMemeEtablissement(Long personnelId) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Personnel p = personnelRepository.findById(personnelId).orElseThrow();
        if (etabId == null || !etabId.equals(p.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Membre du personnel introuvable dans cet établissement.");
        }
        return p;
    }

    // Retrouve la fiche personnel liee au compte de connexion actuellement authentifie
    // (rattachement par email, cf. PersonnelController.creerCompte).
    private Personnel personnelDeLUtilisateurConnecte() {
        Long etabId = etablissementService.getCurrentEtablissementId();
        var u = etablissementService.getCurrentUtilisateur();
        if (u == null || etabId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Utilisateur non authentifie.");
        }
        return personnelRepository.findByEmailAndEtablissementId(u.getEmail(), etabId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Aucune fiche personnel n'est associee a votre compte. Contactez l'administration."));
    }

    // ===== CONTRATS =====
    @PostMapping("/contrats")
    public String ajouterContrat(
            @RequestParam Long personnelId,
            @RequestParam String typeContrat,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam Double salaireBase,
            @RequestParam(required = false) String notes) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        Contrat c = new Contrat();
        c.setPersonnel(personnel);
        c.setTypeContrat(typeContrat); c.setDateDebut(dateDebut); c.setDateFin(dateFin);
        c.setSalaireBase(salaireBase); c.setStatut("ACTIF"); c.setNotes(notes);
        contratRepository.save(c);
        return "redirect:/personnel/" + personnelId + "?saved=true#rh-contrats";
    }

    @PostMapping("/contrats/{id}/supprimer")
    public String supprimerContrat(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = contratRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { contratRepository.delete(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-contrats" : "redirect:/personnel";
    }

    // ===== CONGÉS =====
    @PostMapping("/conges")
    public String ajouterConge(
            @RequestParam Long personnelId,
            @RequestParam String typeConge,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String motif) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        Conge c = new Conge();
        c.setPersonnel(personnel);
        c.setTypeConge(typeConge); c.setDateDebut(dateDebut); c.setDateFin(dateFin);
        c.setJoursNombre((int) ChronoUnit.DAYS.between(dateDebut, dateFin) + 1);
        c.setStatut("EN_ATTENTE"); c.setMotif(motif);
        congeRepository.save(c);
        return "redirect:/personnel/" + personnelId + "?saved=true#rh-conges";
    }

    @PostMapping("/conges/{id}/approuver")
    public String approuverConge(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { c.setStatut("APPROUVE"); congeRepository.save(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-conges" : "redirect:/personnel";
    }

    @PostMapping("/conges/{id}/refuser")
    public String refuserConge(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { c.setStatut("REFUSE"); congeRepository.save(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-conges" : "redirect:/personnel";
    }

    @PostMapping("/conges/{id}/supprimer")
    public String supprimerConge(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Long pid = congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && etabId != null && etabId.equals(c.getPersonnel().getEtablissementId()))
            .map(c -> { congeRepository.delete(c); return c.getPersonnel().getId(); })
            .orElse(null);
        return pid != null ? "redirect:/personnel/" + pid + "#rh-conges" : "redirect:/personnel";
    }

    // ===== CONGÉS — auto-service enseignant =====
    // Un enseignant demande son propre conge (la fiche personnel est deduite de son
    // compte connecte, jamais transmise par le client) ; l'ADMIN garde la main pour
    // approuver/refuser via les endpoints ci-dessus.
    @PostMapping("/conges/demander")
    public String demanderConge(
            @RequestParam String typeConge,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String motif) {

        Personnel personnel = personnelDeLUtilisateurConnecte();
        Conge c = new Conge();
        c.setPersonnel(personnel);
        c.setTypeConge(typeConge); c.setDateDebut(dateDebut); c.setDateFin(dateFin);
        c.setJoursNombre((int) ChronoUnit.DAYS.between(dateDebut, dateFin) + 1);
        c.setStatut("EN_ATTENTE"); c.setMotif(motif);
        congeRepository.save(c);
        return "redirect:/tableau-enseignant?saved=true#mes-conges";
    }

    @PostMapping("/conges/{id}/annuler")
    public String annulerConge(@PathVariable Long id) {
        Personnel personnel = personnelDeLUtilisateurConnecte();
        congeRepository.findById(id)
            .filter(c -> c.getPersonnel() != null && personnel.getId().equals(c.getPersonnel().getId()))
            .filter(c -> "EN_ATTENTE".equals(c.getStatut()))
            .ifPresent(congeRepository::delete);
        return "redirect:/tableau-enseignant?saved=true#mes-conges";
    }

    // ===== SALAIRES / BULLETINS DE PAIE =====

    private static final List<String> LIBELLES_GAIN = List.of(
        "Salaire de base", "Prime de responsabilité", "Congé annuel", "Indemnité de fin de contrat");

    @GetMapping("/salaires/nouveau")
    public String nouveauBulletin(
            @RequestParam Long personnelId,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee,
            Model model) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        Long etabId = etablissementService.getCurrentEtablissementId();

        LocalDate maintenant = LocalDate.now();
        int moisChoisi = mois != null ? mois : maintenant.getMonthValue();
        int anneeChoisie = annee != null ? annee : maintenant.getYear();
        YearMonth periode = YearMonth.of(anneeChoisie, moisChoisi);

        Contrat contrat = contratRepository.findByPersonnelId(personnelId).stream()
            .filter(c -> "ACTIF".equals(c.getStatut()))
            .max(Comparator.comparing(Contrat::getDateDebut, Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(null);

        Map<String, String> taux = parametreRepository.findByCategorieAndEtablissementIdOrderByCleAsc("PAIE", etabId).stream()
            .collect(Collectors.toMap(Parametre::getCle, Parametre::getValeur, (a, b) -> a));

        Long anciennete = personnel.getDateEmbauche() != null
            ? Period.between(personnel.getDateEmbauche(), periode.atEndOfMonth()).toTotalMonths()
            : null;

        model.addAttribute("personnel", personnel);
        model.addAttribute("contrat", contrat);
        model.addAttribute("mois", moisChoisi);
        model.addAttribute("annee", anneeChoisie);
        model.addAttribute("anciennete", anciennete);
        model.addAttribute("taux", taux);
        return "rh-salaire-nouveau";
    }

    @PostMapping("/salaires")
    public String ajouterSalaire(
            @RequestParam Long personnelId,
            @RequestParam int mois,
            @RequestParam int annee,
            @RequestParam(defaultValue = "0") Double salaireBase,
            @RequestParam(defaultValue = "0") Double primeResponsabilite,
            @RequestParam(defaultValue = "0") Double congeAnnuel,
            @RequestParam(defaultValue = "0") Double indemniteFinContrat,
            @RequestParam(defaultValue = "0") Double retenueCnpsEmploye,
            @RequestParam(defaultValue = "0") Double irppSurSalaire,
            @RequestParam(defaultValue = "0") Double chargeTaxeApprentissage,
            @RequestParam(defaultValue = "0") Double chargeTaxeForfaitaire,
            @RequestParam(defaultValue = "0") Double chargeCnpsAccident,
            @RequestParam(defaultValue = "0") Double chargeCnpsAllocFam,
            @RequestParam(defaultValue = "0") Double chargeCnpsPension,
            @RequestParam(required = false) Double tauxCnpsEmploye,
            @RequestParam(required = false) Double tauxIrpp,
            @RequestParam(required = false) Double tauxTaxeApprentissage,
            @RequestParam(required = false) Double tauxTaxeForfaitaire,
            @RequestParam(required = false) Double tauxCnpsAccident,
            @RequestParam(required = false) Double tauxCnpsAllocFam,
            @RequestParam(required = false) Double tauxCnpsPension,
            @RequestParam(defaultValue = "false") boolean activerPrime,
            @RequestParam(defaultValue = "false") boolean activerConge,
            @RequestParam(defaultValue = "false") boolean activerIndemnite,
            @RequestParam(defaultValue = "false") boolean activerCnpsEmploye,
            @RequestParam(defaultValue = "false") boolean activerIrpp,
            @RequestParam(defaultValue = "false") boolean activerTaxeApprentissage,
            @RequestParam(defaultValue = "false") boolean activerTaxeForfaitaire,
            @RequestParam(defaultValue = "false") boolean activerCnpsAccident,
            @RequestParam(defaultValue = "false") boolean activerCnpsAllocFam,
            @RequestParam(defaultValue = "false") boolean activerCnpsPension) {

        Personnel personnel = personnelDuMemeEtablissement(personnelId);
        YearMonth periode = YearMonth.of(annee, mois);
        anneeScolaireService.verifierModifiable(AnneeScolaireUtil.pour(periode.atDay(1)), etablissementService.getCurrentEtablissementId());

        double primeEff = activerPrime ? primeResponsabilite : 0;
        double congeEff = activerConge ? congeAnnuel : 0;
        double indemniteEff = activerIndemnite ? indemniteFinContrat : 0;
        double totalBrut = salaireBase + primeEff + congeEff + indemniteEff;

        double retenueCnpsEmployeEff = activerCnpsEmploye ? retenueCnpsEmploye : 0;
        double irppSurSalaireEff = activerIrpp ? irppSurSalaire : 0;
        double netSoumisIrpp = totalBrut - retenueCnpsEmployeEff;
        double totalRetenues = retenueCnpsEmployeEff + irppSurSalaireEff;

        double taxeApprentissageEff = activerTaxeApprentissage ? chargeTaxeApprentissage : 0;
        double taxeForfaitaireEff = activerTaxeForfaitaire ? chargeTaxeForfaitaire : 0;
        double cnpsAccidentEff = activerCnpsAccident ? chargeCnpsAccident : 0;
        double cnpsAllocFamEff = activerCnpsAllocFam ? chargeCnpsAllocFam : 0;
        double cnpsPensionEff = activerCnpsPension ? chargeCnpsPension : 0;
        double totalCharges = taxeApprentissageEff + taxeForfaitaireEff
            + cnpsAccidentEff + cnpsAllocFamEff + cnpsPensionEff;

        SalaireMensuel s = new SalaireMensuel();
        s.setPersonnel(personnel);
        s.setMois(mois); s.setAnnee(annee);
        s.setPeriodeDebut(periode.atDay(1)); s.setPeriodeFin(periode.atEndOfMonth());
        s.setAnneeScolaire(AnneeScolaireUtil.pour(periode.atDay(1)));
        s.setStatut("EN_ATTENTE");
        s.setTotalBrut(totalBrut);
        s.setTotalRetenuesSalariales(totalRetenues);
        s.setTotalChargesPatronales(totalCharges);
        s.setNetAPayer(totalBrut - totalRetenues);

        List<LigneSalaire> lignes = new java.util.ArrayList<>();
        int ordre = 0;
        ordre = ajouterLigne(lignes, s, ordre, "GAIN", "Salaire de base", null, null, salaireBase);
        if (activerPrime) ordre = ajouterLigne(lignes, s, ordre, "GAIN", "Prime de responsabilité", null, null, primeResponsabilite);
        if (activerConge) ordre = ajouterLigne(lignes, s, ordre, "GAIN", "Congé annuel", null, null, congeAnnuel);
        if (activerIndemnite) ordre = ajouterLigne(lignes, s, ordre, "GAIN", "Indemnité de fin de contrat", null, null, indemniteFinContrat);
        if (activerCnpsEmploye) ordre = ajouterLigne(lignes, s, ordre, "RETENUE_SALARIALE", "Cotisation CNPS - Employé", totalBrut, tauxCnpsEmploye, retenueCnpsEmploye);
        if (activerIrpp) ordre = ajouterLigne(lignes, s, ordre, "RETENUE_SALARIALE", "IRPP sur salaire", netSoumisIrpp, tauxIrpp, irppSurSalaire);
        if (activerTaxeApprentissage) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "Taxe d'apprentissage", netSoumisIrpp, tauxTaxeApprentissage, chargeTaxeApprentissage);
        if (activerTaxeForfaitaire) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "Taxe forfaitaire", netSoumisIrpp, tauxTaxeForfaitaire, chargeTaxeForfaitaire);
        if (activerCnpsAccident) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "CNPS accident de travail", totalBrut, tauxCnpsAccident, chargeCnpsAccident);
        if (activerCnpsAllocFam) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "CNPS allocations familiales", totalBrut, tauxCnpsAllocFam, chargeCnpsAllocFam);
        if (activerCnpsPension) ordre = ajouterLigne(lignes, s, ordre, "CHARGE_PATRONALE", "CNPS pension vieillesse", totalBrut, tauxCnpsPension, chargeCnpsPension);
        s.setLignes(lignes);

        salaireRepository.save(s);
        return "redirect:/personnel/" + personnelId + "?saved=true#rh-salaires";
    }

    private int ajouterLigne(List<LigneSalaire> lignes, SalaireMensuel s, int ordre,
                              String section, String libelle, Double base, Double taux, Double montant) {
        LigneSalaire l = new LigneSalaire();
        l.setSalaireMensuel(s); l.setSection(section); l.setLibelle(libelle);
        l.setBase(base); l.setTaux(taux); l.setMontant(montant); l.setOrdre(ordre);
        lignes.add(l);
        return ordre + 1;
    }

    @GetMapping("/salaires/{id}/bulletin")
    public String voirBulletin(@PathVariable Long id, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SalaireMensuel s = salaireRepository.findById(id)
            .filter(sm -> sm.getPersonnel() != null && etabId != null && etabId.equals(sm.getPersonnel().getEtablissementId()))
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Bulletin introuvable."));

        List<LigneSalaire> lignes = ligneSalaireRepository.findBySalaireMensuelIdOrderByOrdreAsc(id);
        Map<String, List<LigneSalaire>> parSection = lignes.stream()
            .collect(Collectors.groupingBy(LigneSalaire::getSection, LinkedHashMap::new, Collectors.toList()));

        Personnel personnel = s.getPersonnel();
        Long anciennete = personnel.getDateEmbauche() != null && s.getPeriodeFin() != null
            ? Period.between(personnel.getDateEmbauche(), s.getPeriodeFin()).toTotalMonths()
            : null;

        Map<String, String> params = parametreRepository.findByEtablissementId(etabId).stream()
            .collect(Collectors.toMap(Parametre::getCle, Parametre::getValeur, (a, b) -> a));
        Etablissement etab = etablissementService.getCurrentEtablissement();

        model.addAttribute("bulletin", s);
        model.addAttribute("personnel", personnel);
        model.addAttribute("lignesGains", parSection.getOrDefault("GAIN", List.of()));
        model.addAttribute("lignesRetenues", parSection.getOrDefault("RETENUE_SALARIALE", List.of()));
        model.addAttribute("lignesCharges", parSection.getOrDefault("CHARGE_PATRONALE", List.of()));
        model.addAttribute("anciennete", anciennete);
        model.addAttribute("nomEtab", etab != null && etab.getNom() != null && !etab.getNom().isBlank() ? etab.getNom() : "HolyFlame");
        model.addAttribute("adresseEtab", etab != null && etab.getAdresse() != null ? etab.getAdresse() : "");
        model.addAttribute("telEtab", params.getOrDefault("TELEPHONE_ECOLE", ""));
        model.addAttribute("logoPath", params.getOrDefault("LOGO_ETAB", null));
        model.addAttribute("monnaie", params.getOrDefault("MONNAIE", "FCFA"));
        return "bulletin-paie";
    }

    @PostMapping("/salaires/{id}/payer")
    public String payerSalaire(@PathVariable Long id) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SalaireMensuel s = salaireRepository.findById(id)
            .filter(sm -> sm.getPersonnel() != null && etabId != null && etabId.equals(sm.getPersonnel().getEtablissementId()))
            .orElse(null);
        if (s == null) return "redirect:/personnel";
        Long pid = s.getPersonnel().getId();
        if ("PAYE".equals(s.getStatut())) {
            return "redirect:/personnel/" + pid + "#rh-salaires";
        }
        anneeScolaireService.verifierModifiable(s.getAnneeScolaire(), etabId);

        s.setStatut("PAYE");
        s.setDatePaiement(LocalDate.now());
        salaireRepository.save(s);

        Personnel personnel = s.getPersonnel();
        Contrat contratActif = contratRepository.findByPersonnelId(pid).stream()
            .filter(c -> "ACTIF".equals(c.getStatut()))
            .max(Comparator.comparing(Contrat::getDateDebut, Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(null);
        String codeCategorieBrut = contratActif == null ? "66161"
            : switch (contratActif.getTypeContrat()) {
                case "VACATAIRE" -> "66120";
                case "CDI", "CDD" -> "66110";
                default -> "66161";
            };

        String libellePeriode = s.getMois() + "/" + s.getAnnee();
        String nomPersonnel = personnel.getPrenom() + " " + personnel.getNom();

        categorieComptableRepository.findByCodeAndEtablissementId(codeCategorieBrut, etabId).ifPresent(cat -> {
            Depense d = new Depense();
            d.setDesignation("Salaire " + libellePeriode + " - " + nomPersonnel);
            d.setCategorieComptable(cat);
            d.setSens("CHARGE");
            d.setBeneficiaire(nomPersonnel);
            d.setMontant(s.getTotalBrut());
            d.setDateDepense(LocalDate.now());
            d.setStatut("PAYE");
            d.setAnneeScolaire(AnneeScolaireUtil.pour(LocalDate.now()));
            d.setEtablissementId(etabId);
            depenseRepository.save(d);
        });

        if (s.getTotalChargesPatronales() != null && s.getTotalChargesPatronales() > 0) {
            categorieComptableRepository.findByCodeAndEtablissementId("66420", etabId).ifPresent(cat -> {
                Depense d = new Depense();
                d.setDesignation("Charges patronales sur salaire " + libellePeriode + " - " + nomPersonnel);
                d.setCategorieComptable(cat);
                d.setSens("CHARGE");
                d.setBeneficiaire(nomPersonnel);
                d.setMontant(s.getTotalChargesPatronales());
                d.setDateDepense(LocalDate.now());
                d.setStatut("PAYE");
                d.setAnneeScolaire(AnneeScolaireUtil.pour(LocalDate.now()));
                d.setEtablissementId(etabId);
                depenseRepository.save(d);
            });
        }

        return "redirect:/personnel/" + pid + "#rh-salaires";
    }

    @PostMapping("/salaires/{id}/supprimer")
    public String supprimerSalaire(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        SalaireMensuel s = salaireRepository.findById(id)
            .filter(sm -> sm.getPersonnel() != null && etabId != null && etabId.equals(sm.getPersonnel().getEtablissementId()))
            .orElse(null);
        if (s == null) return "redirect:/personnel";
        Long pid = s.getPersonnel().getId();
        if ("PAYE".equals(s.getStatut())) {
            ra.addFlashAttribute("erreurMsg", "Ce bulletin est déjà payé et a été comptabilisé — il ne peut plus être supprimé.");
            return "redirect:/personnel/" + pid + "#rh-salaires";
        }
        salaireRepository.delete(s);
        return "redirect:/personnel/" + pid + "#rh-salaires";
    }
}
