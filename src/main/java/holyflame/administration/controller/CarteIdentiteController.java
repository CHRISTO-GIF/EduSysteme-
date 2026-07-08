package holyflame.administration.controller;

import holyflame.administration.model.Eleve;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.DocumentEleveRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.service.EtablissementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/secretariat/cartes")
public class CarteIdentiteController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private DocumentEleveRepository documentEleveRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private EtablissementService etablissementService;

    @GetMapping
    public String index(@RequestParam(required = false) String q,
                        @RequestParam(required = false) Long classeId,
                        @RequestParam(required = false) Long eleveId,
                        Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();

        List<Eleve> eleves;
        if (q != null && !q.isBlank()) {
            eleves = eleveRepository.searchByEtablissement(q.trim(), etabId);
        } else if (classeId != null) {
            eleves = eleveRepository.findByClasseIdOrderByNomAsc(classeId).stream()
                .filter(e -> etabId.equals(e.getEtablissementId()))
                .toList();
        } else {
            eleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        }

        // Selection restreinte a la liste deja filtree par etablissement (isolation multi-tenant)
        Eleve selection = null;
        if (eleveId != null) {
            selection = eleves.stream().filter(e -> e.getId().equals(eleveId)).findFirst().orElse(null);
        }
        if (selection == null && !eleves.isEmpty()) {
            selection = eleves.get(0);
        }

        Map<Long, String> photosParEleve = new LinkedHashMap<>();
        for (Eleve e : eleves) {
            documentEleveRepository.findByEleveIdOrderByDateUploadDesc(e.getId()).stream()
                .filter(d -> "PHOTO_IDENTITE".equals(d.getTypeDocument()))
                .findFirst()
                .ifPresent(d -> photosParEleve.put(e.getId(), d.getCheminFichier()));
        }

        String photoSelection = selection != null ? photosParEleve.get(selection.getId()) : null;

        holyflame.administration.model.Etablissement etab = etablissementService.getCurrentEtablissement();
        String nomEtab = etab != null && etab.getNom() != null && !etab.getNom().isBlank() ? etab.getNom() : "HolyFlame";
        String devise = parametre(etabId, "DEVISE", "");
        String adresseEtab = etab != null && etab.getAdresse() != null ? etab.getAdresse() : "";
        String telEtab = parametre(etabId, "TELEPHONE_ECOLE", "");
        String emailEtab = parametre(etabId, "EMAIL_ECOLE", "");
        String logoPath = parametre(etabId, "LOGO_ETAB", null);
        String chefEtablissement = parametre(etabId, "CONTACT_PRINCIPAL", null);

        String anneeScolaire = selection != null && selection.getClasse() != null
            ? selection.getClasse().getAnneeScolaire()
            : parametre(etabId, "ANNEE_SCOLAIRE", "2025-2026");

        model.addAttribute("eleves", eleves);
        model.addAttribute("photosParEleve", photosParEleve);
        model.addAttribute("classes", classeRepository.findByEtablissementId(etabId));
        model.addAttribute("classeId", classeId);
        model.addAttribute("q", q);
        model.addAttribute("selection", selection);
        model.addAttribute("photoSelection", photoSelection);
        model.addAttribute("nomEtab", nomEtab);
        model.addAttribute("devise", devise);
        model.addAttribute("adresseEtab", adresseEtab);
        model.addAttribute("telEtab", telEtab);
        model.addAttribute("emailEtab", emailEtab);
        model.addAttribute("logoPath", logoPath);
        model.addAttribute("chefEtablissement", chefEtablissement);
        model.addAttribute("anneeScolaire", anneeScolaire);
        return "carte-identite";
    }

    private String parametre(Long etabId, String cle, String defaut) {
        return parametreRepository.findByCleAndEtablissementId(cle, etabId)
            .map(p -> p.getValeur())
            .filter(v -> v != null && !v.isBlank())
            .orElse(defaut);
    }
}
