package holyflame.administration.controller;

import holyflame.administration.model.Conduite;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.ConduiteRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.service.BulletinService;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/bulletins")
public class BulletinController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private ConduiteRepository conduiteRepository;
    @Autowired private EtablissementService etablissementService;
    @Autowired private BulletinService bulletinService;

    @GetMapping
    public String liste(@RequestParam(required = false) Long classeId,
                        @RequestParam(required = false) String q, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Eleve> eleves = classeId != null
            ? eleveRepository.findByClasseIdOrderByNomAsc(classeId)
            : eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);

        if (q != null && !q.isBlank()) {
            String terme = q.trim().toLowerCase();
            eleves = eleves.stream()
                .filter(e -> (e.getNom() != null && e.getNom().toLowerCase().contains(terme))
                    || (e.getPrenom() != null && e.getPrenom().toLowerCase().contains(terme))
                    || (e.getMatricule() != null && e.getMatricule().toLowerCase().contains(terme)))
                .toList();
        }

        model.addAttribute("eleves",  eleves);
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("classeId", classeId);
        model.addAttribute("q", q);
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "bulletins";
    }

    // ── Hub des options d'impression (individuelle / par classe / etablissement) ──
    @GetMapping("/options")
    public String options(Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        return "bulletins-options";
    }

    // ── Impression en lot : tous les bulletins d'une classe pour un trimestre ──
    @GetMapping("/impression")
    public String impressionClasse(@RequestParam(required = false) Long classeId,
                                   @RequestParam(defaultValue = "1") Integer trimestre,
                                   Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();

        List<Eleve> eleves = classeId != null
            ? eleveRepository.findByClasseIdOrderByNomAsc(classeId)
            : eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);

        List<Map<String, Object>> bulletins = new ArrayList<>();
        for (Eleve eleve : eleves) {
            bulletins.add(bulletinService.calculerBulletin(eleve, trimestre, etabId));
        }

        // ── En-tete etablissement (parametres reels), identique au bulletin individuel ──
        holyflame.administration.model.Etablissement etabListe = etablissementService.getCurrentEtablissement();
        String nomEtab = etabListe != null && etabListe.getNom() != null && !etabListe.getNom().isBlank()
            ? etabListe.getNom() : "HolyFlame";
        String adresseEtab = etabListe != null && etabListe.getAdresse() != null ? etabListe.getAdresse() : "";
        String emailEtab = parametreRepository.findByCleAndEtablissementId("EMAIL_ECOLE", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse("");
        String logoPath = parametreRepository.findByCleAndEtablissementId("LOGO_ETAB", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse(null);
        String devise = parametreRepository.findByCleAndEtablissementId("DEVISE", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse(null);
        String chefEtablissement = parametreRepository.findByCleAndEtablissementId("CONTACT_PRINCIPAL", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse(null);
        String classeNom = classeId != null
            ? classeRepository.findById(classeId).map(c -> c.getNom()).orElse("Toutes les classes")
            : "Toutes les classes";

        model.addAttribute("bulletins",  bulletins);
        model.addAttribute("trimestre",  trimestre);
        model.addAttribute("classeNom",  classeNom);
        model.addAttribute("nomEtab",    nomEtab);
        model.addAttribute("adresseEtab", adresseEtab);
        model.addAttribute("emailEtab",  emailEtab);
        model.addAttribute("logoPath",   logoPath);
        model.addAttribute("devise",     devise);
        model.addAttribute("chefEtablissement", chefEtablissement);
        model.addAttribute("classeId",   classeId);
        model.addAttribute("classes",    classeRepository.findByEtablissementId(etabId));
        model.addAttribute("trimestres", List.of(1, 2, 3));
        return "bulletins-impression";
    }

    @GetMapping("/{eleveId}")
    public String bulletin(@PathVariable Long eleveId,
                           @RequestParam(defaultValue = "1") Integer trimestre,
                           Model model,
                           RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Eleve eleve = eleveRepository.findById(eleveId).orElse(null);
        if (eleve == null || (etabId != null && !etabId.equals(eleve.getEtablissementId()))) {
            ra.addFlashAttribute("erreur", "Élève introuvable.");
            return "redirect:/bulletins";
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean estParent = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_PARENT".equals(a.getAuthority()));
        if (estParent) {
            String email = auth.getName();
            boolean estMonEnfant = email.equalsIgnoreCase(eleve.getEmailParent())
                || email.equalsIgnoreCase(eleve.getPereEmail())
                || email.equalsIgnoreCase(eleve.getMereEmail());
            if (!estMonEnfant) {
                ra.addFlashAttribute("erreur", "Vous n'avez pas accès au bulletin de cet élève.");
                return "redirect:/portail-parent";
            }
        }

        Map<String, Object> donnees = bulletinService.calculerBulletin(eleve, trimestre, etabId);

        // ── Chef d'etablissement et en-tete (parametres reels) ──
        String chefEtablissement = parametreRepository.findByCleAndEtablissementId("CONTACT_PRINCIPAL", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse(null);
        holyflame.administration.model.Etablissement etab = etablissementService.getCurrentEtablissement();
        String nomEtab = etab != null && etab.getNom() != null && !etab.getNom().isBlank() ? etab.getNom() : "HolyFlame";
        String adresseEtab = etab != null && etab.getAdresse() != null ? etab.getAdresse() : "";
        String emailEtab = parametreRepository.findByCleAndEtablissementId("EMAIL_ECOLE", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse("");
        String logoPath = parametreRepository.findByCleAndEtablissementId("LOGO_ETAB", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse(null);
        String devise = parametreRepository.findByCleAndEtablissementId("DEVISE", etabId)
            .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse(null);

        model.addAllAttributes(donnees);
        model.addAttribute("trimestre", trimestre);
        model.addAttribute("chefEtablissement", chefEtablissement);
        model.addAttribute("nomEtab", nomEtab);
        model.addAttribute("adresseEtab", adresseEtab);
        model.addAttribute("emailEtab", emailEtab);
        model.addAttribute("logoPath", logoPath);
        model.addAttribute("devise", devise);
        boolean estAdminOuEnseignant = auth != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_ENSEIGNANT".equals(a.getAuthority()));
        model.addAttribute("estAdminOuEnseignant", estAdminOuEnseignant);
        model.addAttribute("baseUrl", "/bulletins/" + eleve.getId());
        model.addAttribute("retourUrl", estAdminOuEnseignant ? "/bulletins" : (estParent ? "/portail-parent" : "/portail"));
        return "bulletin";
    }

    @PostMapping("/{eleveId}/conduite")
    public String enregistrerConduite(@PathVariable Long eleveId,
                                      @RequestParam Integer trimestre,
                                      @RequestParam String evaluation,
                                      @RequestParam(required = false) String commentaire,
                                      RedirectAttributes ra) {
        Eleve eleve = eleveRepository.findById(eleveId).orElseThrow();
        String anneeScolaire = eleve.getClasse() != null ? eleve.getClasse().getAnneeScolaire() : null;
        if (anneeScolaire == null) {
            ra.addFlashAttribute("erreur", "Impossible d'enregistrer la conduite : aucune classe/annee scolaire associee.");
            return "redirect:/bulletins/" + eleveId + "?trimestre=" + trimestre;
        }
        Conduite conduite = conduiteRepository.findByEleveAndTrimestreAndAnneeScolaire(eleve, trimestre, anneeScolaire)
            .orElseGet(Conduite::new);
        conduite.setEleve(eleve);
        conduite.setTrimestre(trimestre);
        conduite.setAnneeScolaire(anneeScolaire);
        conduite.setEvaluation(evaluation);
        conduite.setCommentaire(commentaire);
        conduite.setSaisieAt(LocalDateTime.now());
        Utilisateur saisiPar = etablissementService.getCurrentUtilisateur();
        if (saisiPar != null) conduite.setSaisieParId(saisiPar.getId());
        conduiteRepository.save(conduite);
        return "redirect:/bulletins/" + eleveId + "?trimestre=" + trimestre;
    }
}
