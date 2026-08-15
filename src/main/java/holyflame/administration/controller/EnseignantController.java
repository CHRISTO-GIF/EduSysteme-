package holyflame.administration.controller;

import holyflame.administration.model.*;
import holyflame.administration.repository.*;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tableau-enseignant")
public class EnseignantController {

    @Autowired private EnseignantAutorisationRepository autorisationRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private EleveRepository eleveRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private CongeRepository congeRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private holyflame.administration.service.HorlogeService horlogeService;

    @GetMapping
    public String index(Model model) {
        Utilisateur currentUser = etablissementService.getCurrentUtilisateur();
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", currentUser);

        if (currentUser != null && "ENSEIGNANT".equals(currentUser.getRole())) {
            List<EnseignantAutorisation> auths = autorisationRepository
                .findByEnseignantIdAndEtablissementId(currentUser.getId(), etabId);

            // Maps pour les noms
            Map<Long, Matiere> matiereMap = matiereRepository.findByEtablissementIdOrderByNomAsc(etabId)
                .stream().collect(Collectors.toMap(Matiere::getId, m -> m));
            Map<Long, Classe> classeMap = classeRepository.findByEtablissementId(etabId)
                .stream().collect(Collectors.toMap(Classe::getId, c -> c));

            Set<Long> mesClasseIds = auths.stream().map(EnseignantAutorisation::getClasseId).collect(Collectors.toSet());
            Set<Long> mesMatiereIds = auths.stream().map(EnseignantAutorisation::getMatiereId).collect(Collectors.toSet());
            long totalEleves = mesClasseIds.stream().mapToLong(eleveRepository::countByClasseId).sum();

            model.addAttribute("autorisations", auths);
            model.addAttribute("matiereMap", matiereMap);
            model.addAttribute("classeMap", classeMap);
            model.addAttribute("hasAutorisations", !auths.isEmpty());
            model.addAttribute("totalClasses", mesClasseIds.size());
            model.addAttribute("totalMatieres", mesMatiereIds.size());
            model.addAttribute("totalEleves", totalEleves);

            // Fiche personnel rattachee au compte (par email) : necessaire pour "Mes conges"
            Personnel monPersonnel = currentUser.getEmail() != null && etabId != null
                ? personnelRepository.findByEmailAndEtablissementId(currentUser.getEmail(), etabId).orElse(null)
                : null;
            model.addAttribute("personnelLie", monPersonnel != null);
            model.addAttribute("mesConges", monPersonnel != null
                ? congeRepository.findByPersonnelId(monPersonnel.getId()).stream()
                    .sorted(Comparator.comparing(Conge::getDateDebut, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList())
                : List.of());
        } else {
            model.addAttribute("autorisations", List.of());
            model.addAttribute("hasAutorisations", false);
            model.addAttribute("totalClasses", 0);
            model.addAttribute("totalMatieres", 0);
            model.addAttribute("totalEleves", 0L);
            model.addAttribute("personnelLie", false);
            model.addAttribute("mesConges", List.of());
        }

        return "tableau-enseignant";
    }

    @GetMapping("/eleves")
    public String mesEleves(@RequestParam(required = false) Long classeId, Model model) {
        Utilisateur currentUser = etablissementService.getCurrentUtilisateur();
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", currentUser);

        List<EnseignantAutorisation> auths = (currentUser != null && "ENSEIGNANT".equals(currentUser.getRole()))
            ? autorisationRepository.findByEnseignantIdAndEtablissementId(currentUser.getId(), etabId)
            : List.of();

        Set<Long> mesClasseIds = auths.stream().map(EnseignantAutorisation::getClasseId).collect(Collectors.toSet());
        Set<Long> mesMatiereIds = auths.stream().map(EnseignantAutorisation::getMatiereId).collect(Collectors.toSet());

        List<Classe> mesClasses = classeRepository.findByEtablissementId(etabId).stream()
            .filter(c -> mesClasseIds.contains(c.getId()))
            .sorted(Comparator.comparing(Classe::getNom))
            .collect(Collectors.toList());

        List<Eleve> eleves = new ArrayList<>();
        for (Long cid : mesClasseIds) {
            if (classeId != null && !classeId.equals(cid)) continue;
            eleves.addAll(eleveRepository.findByClasseIdOrderByNomAsc(cid));
        }
        eleves.sort(Comparator.comparing(Eleve::getNom, Comparator.nullsLast(Comparator.naturalOrder())));

        // Absences ce mois, pour ces eleves uniquement
        Set<Long> eleveIds = eleves.stream().map(Eleve::getId).collect(Collectors.toSet());
        int moisActuel = horlogeService.aujourdHui().getMonthValue();
        int anneeActuelle = horlogeService.aujourdHui().getYear();
        long absencesCeMois = absenceRepository.findByEtablissementId(etabId).stream()
            .filter(a -> a.getEleve() != null && eleveIds.contains(a.getEleve().getId()))
            .filter(a -> a.getDate() != null && a.getDate().getMonthValue() == moisActuel && a.getDate().getYear() == anneeActuelle)
            .count();

        model.addAttribute("eleves", eleves);
        model.addAttribute("mesClasses", mesClasses);
        model.addAttribute("selectedClasseId", classeId);
        model.addAttribute("totalEleves", eleves.size());
        model.addAttribute("totalClasses", mesClasses.size());
        model.addAttribute("totalMatieres", mesMatiereIds.size());
        model.addAttribute("absencesCeMois", absencesCeMois);
        return "tableau-enseignant-eleves";
    }
}
