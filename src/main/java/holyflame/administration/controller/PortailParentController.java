package holyflame.administration.controller;

import holyflame.administration.model.Absence;
import holyflame.administration.model.Classe;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.EvenementEcole;
import holyflame.administration.model.Examen;
import holyflame.administration.model.Note;
import holyflame.administration.model.RappelParent;
import holyflame.administration.repository.AbsenceRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EvenementEcoleRepository;
import holyflame.administration.repository.ExamenRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.repository.RappelParentRepository;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/portail-parent")
public class PortailParentController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private ExamenRepository examenRepository;
    @Autowired private EvenementEcoleRepository evenementEcoleRepository;
    @Autowired private RappelParentRepository rappelParentRepository;
    @Autowired private EtablissementService etablissementService;

    private static final String[] COULEURS_ENFANTS = {"blue", "pink", "green", "orange", "purple", "teal"};

    @GetMapping
    public String index(Authentication auth, Model model) {
        String email = auth != null ? auth.getName() : "";
        List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(email);

        List<Map<String, Object>> cartesEnfants = new java.util.ArrayList<>();
        int totalDevoirsRecents = 0;
        int totalExamensAVenir = 0;

        for (Eleve enfant : enfants) {
            Classe classe = enfant.getClasse();

            List<Note> notesPubliees = noteRepository.findByEleveOrderByDateEvaluationDesc(enfant).stream()
                .filter(n -> "PUBLIE".equals(n.getStatut()))
                .toList();
            double moyenne = 0;
            double somme = 0, totalCoef = 0;
            for (Note n : notesPubliees) {
                if (n.getValeur() != null && n.getCoefficient() != null) {
                    somme += n.getValeur() * n.getCoefficient();
                    totalCoef += n.getCoefficient();
                }
            }
            if (totalCoef > 0) moyenne = Math.round((somme / totalCoef) * 100.0) / 100.0;

            long nbDevoirsRecents = notesPubliees.stream()
                .filter(n -> "DEVOIR".equals(n.getType()) && n.getDateEvaluation() != null
                    && n.getDateEvaluation().isAfter(LocalDate.now().minusDays(7)))
                .count();
            totalDevoirsRecents += nbDevoirsRecents;

            List<Absence> absences = absenceRepository.findByEleveIdOrderByDateDesc(enfant.getId());
            long nbAbsencesNonJustifiees = absences.stream().filter(a -> !a.isEstJustifiee()).count();
            Absence derniereAbsenceNonJustifiee = absences.stream()
                .filter(a -> !a.isEstJustifiee()).findFirst().orElse(null);

            long joursOuvres = compterJoursOuvres(
                enfant.getDateInscription() != null ? enfant.getDateInscription() : LocalDate.now().minusMonths(3),
                LocalDate.now());
            double tauxAssiduite = joursOuvres > 0
                ? Math.max(0, Math.min(100, Math.round((100.0 - (nbAbsencesNonJustifiees * 100.0 / joursOuvres)) * 10) / 10.0))
                : 100;

            Examen prochainExamen = null;
            if (classe != null) {
                prochainExamen = examenRepository.findByEtablissementIdAndClasseIdOrderByDateExamenAscHeureDebutAsc(
                        enfant.getEtablissementId(), classe.getId()).stream()
                    .filter(ex -> ex.getDateExamen() != null && !ex.getDateExamen().isBefore(LocalDate.now()))
                    .findFirst().orElse(null);
                if (prochainExamen != null) totalExamensAVenir++;
            }

            Map<String, Object> carte = new LinkedHashMap<>();
            carte.put("eleve", enfant);
            carte.put("classe", classe);
            carte.put("moyenneGenerale", moyenne);
            carte.put("tauxAssiduite", tauxAssiduite);
            carte.put("nbAbsencesNonJustifiees", nbAbsencesNonJustifiees);
            carte.put("derniereAbsenceNonJustifiee", derniereAbsenceNonJustifiee);
            carte.put("prochainExamen", prochainExamen);
            cartesEnfants.add(carte);
        }

        Etablissement etablissement = etablissementService.getCurrentEtablissement();

        model.addAttribute("enfants", cartesEnfants);
        model.addAttribute("totalDevoirsRecents", totalDevoirsRecents);
        model.addAttribute("totalExamensAVenir", totalExamensAVenir);
        model.addAttribute("emailParent", email);
        model.addAttribute("etablissement", etablissement);
        return "portail-parent";
    }

    // ── Rattacher un enfant existant a ce compte parent (self-service) ──────
    @PostMapping("/ajouter-profil")
    public String ajouterProfil(
            @RequestParam String matricule,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateNaissance,
            Authentication auth,
            RedirectAttributes ra) {

        String email = auth != null ? auth.getName() : "";
        Eleve eleve = eleveRepository.findAll().stream()
            .filter(e -> matricule.equalsIgnoreCase(e.getMatricule()))
            .findFirst().orElse(null);

        if (eleve == null || eleve.getDateNaissance() == null || !eleve.getDateNaissance().equals(dateNaissance)) {
            ra.addFlashAttribute("erreurMsg", "Aucun eleve ne correspond a ce matricule et cette date de naissance.");
            return "redirect:/portail-parent";
        }
        if (eleve.getEmailParent() != null && !eleve.getEmailParent().isBlank() && !eleve.getEmailParent().equalsIgnoreCase(email)) {
            ra.addFlashAttribute("erreurMsg", "Cet eleve est deja rattache a un autre compte parent. Contactez le secretariat.");
            return "redirect:/portail-parent";
        }
        eleve.setEmailParent(email);
        eleveRepository.save(eleve);
        ra.addFlashAttribute("successMsg", eleve.getPrenom() + " " + eleve.getNom() + " a ete rattache(e) a votre compte.");
        return "redirect:/portail-parent";
    }

    // ── Calendrier ────────────────────────────────────────────────────────
    @GetMapping("/calendrier")
    public String calendrier(
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee,
            @RequestParam(defaultValue = "MOIS") String vue,
            Authentication auth,
            Model model) {

        String email = auth != null ? auth.getName() : "";
        List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(email);

        LocalDate aujourdHui = LocalDate.now();
        int moisFiltre = mois != null ? mois : aujourdHui.getMonthValue();
        int anneeFiltre = annee != null ? annee : aujourdHui.getYear();
        LocalDate ancre = LocalDate.of(anneeFiltre, moisFiltre, 1);
        LocalDate debutMois = ancre.withDayOfMonth(1);
        LocalDate finMois = ancre.withDayOfMonth(ancre.lengthOfMonth());

        Map<Long, String> couleurParEnfant = new LinkedHashMap<>();
        for (int i = 0; i < enfants.size(); i++) {
            couleurParEnfant.put(enfants.get(i).getId(), COULEURS_ENFANTS[i % COULEURS_ENFANTS.length]);
        }

        List<Map<String, Object>> tousLesEvenements = new ArrayList<>();

        // Examens reels par enfant
        for (Eleve enfant : enfants) {
            if (enfant.getClasse() == null) continue;
            List<Examen> examens = examenRepository.findByEtablissementIdAndClasseIdOrderByDateExamenAscHeureDebutAsc(
                enfant.getEtablissementId(), enfant.getClasse().getId());
            for (Examen ex : examens) {
                if (ex.getDateExamen() == null) continue;
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("date", ex.getDateExamen());
                evt.put("titre", enfant.getPrenom() + " : " + (ex.getMatiere() != null ? ex.getMatiere().getNom() : "Examen"));
                evt.put("heure", ex.getHeureDebut());
                evt.put("lieu", ex.getSalle());
                evt.put("categorie", "EXAMEN");
                evt.put("enfantId", enfant.getId());
                evt.put("enfantNom", enfant.getPrenom() + " " + enfant.getNom());
                evt.put("couleur", couleurParEnfant.getOrDefault(enfant.getId(), "primary"));
                tousLesEvenements.add(evt);
            }
        }

        // Evenements ecole reels (generaux ou lies a une des classes des enfants)
        List<Long> classesEnfants = enfants.stream()
            .filter(e -> e.getClasse() != null).map(e -> e.getClasse().getId()).distinct().toList();
        Long etabId = etablissementService.getCurrentEtablissementId();
        if (etabId == null && !enfants.isEmpty()) etabId = enfants.get(0).getEtablissementId();
        if (etabId != null) {
            for (EvenementEcole ev : evenementEcoleRepository.findByEtablissementIdOrderByDateEvenementAsc(etabId)) {
                if (ev.getDateEvenement() == null) continue;
                boolean concerne = ev.getClasse() == null || classesEnfants.contains(ev.getClasse().getId());
                if (!concerne) continue;
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("date", ev.getDateEvenement());
                evt.put("titre", ev.getTitre());
                evt.put("heure", ev.getHeureDebut());
                evt.put("lieu", ev.getLieu());
                evt.put("categorie", ev.getCategorie() != null ? ev.getCategorie() : "GENERAL");
                evt.put("enfantId", null);
                evt.put("enfantNom", ev.getClasse() != null ? ev.getClasse().getNom() : "Tout l'etablissement");
                evt.put("couleur", "primary");
                tousLesEvenements.add(evt);
            }
        }

        // Rappels personnels du parent
        for (RappelParent r : rappelParentRepository.findByParentEmailOrderByDateRappelAsc(email)) {
            if (r.getDateRappel() == null) continue;
            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("date", r.getDateRappel());
            evt.put("titre", r.getTitre());
            evt.put("heure", null);
            evt.put("lieu", null);
            evt.put("categorie", "RAPPEL");
            evt.put("enfantId", null);
            evt.put("enfantNom", null);
            evt.put("couleur", "gray");
            evt.put("id", r.getId());
            tousLesEvenements.add(evt);
        }

        tousLesEvenements.sort(Comparator.comparing(e -> (LocalDate) e.get("date")));

        List<Map<String, Object>> evenementsDuMois = tousLesEvenements.stream()
            .filter(e -> {
                LocalDate d = (LocalDate) e.get("date");
                return !d.isBefore(debutMois) && !d.isAfter(finMois);
            }).toList();

        Map<LocalDate, List<Map<String, Object>>> parJour = evenementsDuMois.stream()
            .collect(Collectors.groupingBy(e -> (LocalDate) e.get("date"), LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> evenementsAVenir = tousLesEvenements.stream()
            .filter(e -> !((LocalDate) e.get("date")).isBefore(aujourdHui))
            .limit(5)
            .toList();

        // Grille du mois : premier jour affiche = lundi de la semaine contenant le 1er du mois
        LocalDate debutGrille = debutMois.minusDays((debutMois.getDayOfWeek().getValue() - 1));
        LocalDate finGrille = finMois.plusDays(7 - finMois.getDayOfWeek().getValue());
        LocalDate debutSemaineCourante = aujourdHui.minusDays(aujourdHui.getDayOfWeek().getValue() - 1);
        LocalDate finSemaineCourante = debutSemaineCourante.plusDays(6);
        List<Map<String, Object>> jours = new ArrayList<>();
        LocalDate d = debutGrille;
        while (!d.isAfter(finGrille)) {
            Map<String, Object> jour = new LinkedHashMap<>();
            jour.put("date", d);
            jour.put("horsMois", d.getMonthValue() != moisFiltre);
            jour.put("aujourdHui", d.equals(aujourdHui));
            jour.put("memeSemaine", !d.isBefore(debutSemaineCourante) && !d.isAfter(finSemaineCourante));
            jour.put("evenements", parJour.getOrDefault(d, List.of()));
            jours.add(jour);
            d = d.plusDays(1);
        }

        model.addAttribute("enfants", enfants);
        model.addAttribute("couleurParEnfant", couleurParEnfant);
        model.addAttribute("jours", jours);
        model.addAttribute("evenementsAVenir", evenementsAVenir);
        model.addAttribute("moisFiltre", moisFiltre);
        model.addAttribute("anneeFiltre", anneeFiltre);
        model.addAttribute("nomMois", ancre.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH));
        model.addAttribute("vue", vue);
        model.addAttribute("emailParent", email);
        return "portail-parent-calendrier";
    }

    @PostMapping("/calendrier/rappel")
    public String ajouterRappel(
            @RequestParam String titre,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateRappel,
            @RequestParam(required = false) String notes,
            Authentication auth,
            RedirectAttributes ra) {
        String email = auth != null ? auth.getName() : "";
        RappelParent r = new RappelParent();
        r.setParentEmail(email);
        r.setTitre(titre);
        r.setDateRappel(dateRappel);
        r.setNotes(notes);
        rappelParentRepository.save(r);
        ra.addFlashAttribute("successMsg", "Rappel ajoute.");
        return "redirect:/portail-parent/calendrier?mois=" + dateRappel.getMonthValue() + "&annee=" + dateRappel.getYear();
    }

    @PostMapping("/calendrier/rappel/{id}/supprimer")
    public String supprimerRappel(@org.springframework.web.bind.annotation.PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        String email = auth != null ? auth.getName() : "";
        rappelParentRepository.deleteByIdAndParentEmail(id, email);
        ra.addFlashAttribute("successMsg", "Rappel supprime.");
        return "redirect:/portail-parent/calendrier";
    }

    private long compterJoursOuvres(LocalDate debut, LocalDate fin) {
        long total = 0;
        LocalDate d = debut;
        while (!d.isAfter(fin)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) total++;
            d = d.plusDays(1);
        }
        return total;
    }
}
