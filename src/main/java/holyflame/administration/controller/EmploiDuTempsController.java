package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.CreneauHoraire;
import holyflame.administration.model.Matiere;
import holyflame.administration.model.Personnel;
import holyflame.administration.repository.*;
import holyflame.administration.service.EtablissementService;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/emploi-du-temps")
public class EmploiDuTempsController {

    @Autowired private CreneauHoraireRepository creneauRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private MatiereRepository matiereRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private EtablissementService etablissementService;

    private static final String[] JOURS = {"", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"};

    @GetMapping
    public String index(@RequestParam(required = false) Long classeId, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());

        List<CreneauHoraire> creneaux = classeId != null
            ? creneauRepository.findByEtablissementIdAndClasseIdOrderByJourAscHeureDebutAsc(etabId, classeId)
            : creneauRepository.findByEtablissementIdOrderByJourAscHeureDebutAsc(etabId);

        // Organiser par jour : liste indexee (index 0 = Lundi ... index 5 = Samedi)
        // Utilisation d'une liste plutot qu'une Map<Integer,...> pour eviter les soucis
        // de correspondance de type de cle avec les entiers produits par Thymeleaf (#numbers.sequence).
        List<List<CreneauHoraire>> parJour = new ArrayList<>();
        for (int j = 1; j <= 6; j++) {
            final int jour = j;
            List<CreneauHoraire> liste = creneaux.stream()
                .filter(c -> jour == c.getJour()).collect(Collectors.toList());
            parJour.add(liste);
        }

        holyflame.administration.model.Etablissement etabEdt = etablissementService.getCurrentEtablissement();
        String nomEtab = etabEdt != null && etabEdt.getNom() != null && !etabEdt.getNom().isBlank()
            ? etabEdt.getNom() : "HolyFlame";
        String annee = parametreRepository
            .findByCleAndEtablissementId("ANNEE_SCOLAIRE", etabId)
            .map(p -> p.getValeur()).orElse("2025-2026");

        List<holyflame.administration.model.Classe> classes = classeRepository.findAll().stream()
            .filter(c -> etabId == null || etabId.equals(c.getEtablissementId())).toList();

        String classeNom = classeId != null
            ? classes.stream().filter(c -> c.getId().equals(classeId))
                .findFirst().map(c -> c.getNom()).orElse("Classe inconnue")
            : "Toutes les classes";

        // Statistiques reelles (sur l'ensemble des creneaux de l'etablissement, pas seulement la classe filtree)
        List<CreneauHoraire> tousLesCreneaux = creneauRepository.findByEtablissementIdOrderByJourAscHeureDebutAsc(etabId);
        long totalCreneaux = tousLesCreneaux.size();
        long totalEnseignants = tousLesCreneaux.stream()
            .map(CreneauHoraire::getEnseignantNom)
            .filter(n -> n != null && !n.isBlank())
            .distinct().count();
        long totalSalles = tousLesCreneaux.stream()
            .map(CreneauHoraire::getSalle)
            .filter(s -> s != null && !s.isBlank())
            .distinct().count();

        // Detection reelle des conflits de salle : meme jour + meme salle + horaires qui se chevauchent
        List<String> conflits = trouverConflits(tousLesCreneaux, null);

        // Listes reelles pour les selecteurs du formulaire d'ajout
        List<String> salles = tousLesCreneaux.stream()
            .map(CreneauHoraire::getSalle).filter(s -> s != null && !s.isBlank())
            .distinct().sorted().toList();
        List<Personnel> enseignants = personnelRepository.findByFonctionAndEtablissementIdOrderByNomAsc("ENSEIGNANT", etabId);

        model.addAttribute("parJour",   parJour);
        model.addAttribute("jours",     JOURS);
        model.addAttribute("classes",   classes);
        model.addAttribute("matieres",  matiereRepository.findAll().stream()
            .filter(m -> etabId == null || etabId.equals(m.getEtablissementId())).toList());
        model.addAttribute("salles", salles);
        model.addAttribute("enseignants", enseignants);
        model.addAttribute("classeId",  classeId);
        model.addAttribute("classeNom", classeNom);
        model.addAttribute("nomEtab",   nomEtab);
        model.addAttribute("annee",     annee);
        model.addAttribute("totalCreneaux", totalCreneaux);
        model.addAttribute("totalEnseignants", totalEnseignants);
        model.addAttribute("totalSalles", totalSalles);
        model.addAttribute("conflits", conflits);
        return "emploi-du-temps";
    }

    /**
     * Verification en direct des conflits pour un creneau en cours de saisie (appelee en Ajax depuis la modale).
     * excluId permet d'ignorer le creneau lui-meme lors d'une modification.
     */
    @GetMapping("/verifier-conflit")
    @ResponseBody
    public Map<String, Object> verifierConflit(
            @RequestParam Integer jour,
            @RequestParam String heureDebut,
            @RequestParam String heureFin,
            @RequestParam(required = false) String salle,
            @RequestParam(required = false) String enseignantNom,
            @RequestParam(required = false) Long excluId) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        CreneauHoraire candidat = new CreneauHoraire();
        candidat.setJour(jour);
        candidat.setHeureDebut(heureDebut);
        candidat.setHeureFin(heureFin);
        candidat.setSalle(salle);
        candidat.setEnseignantNom(enseignantNom);

        List<CreneauHoraire> existants = creneauRepository.findByEtablissementIdOrderByJourAscHeureDebutAsc(etabId).stream()
            .filter(c -> excluId == null || !c.getId().equals(excluId))
            .toList();

        List<String> messages = new ArrayList<>();
        for (CreneauHoraire c : existants) {
            if (!creneauxSeChevauchent(candidat, c)) continue;
            if (salle != null && !salle.isBlank() && salle.equalsIgnoreCase(c.getSalle())) {
                messages.add("La salle " + salle + " est deja occupee par "
                    + (c.getMatiere() != null ? c.getMatiere().getNom() : "un cours")
                    + (c.getClasse() != null ? " (" + c.getClasse().getNom() + ")" : "")
                    + " de " + c.getHeureDebut() + " a " + c.getHeureFin() + ".");
            }
            if (enseignantNom != null && !enseignantNom.isBlank() && memeEnseignant(enseignantNom, c.getEnseignantNom())) {
                messages.add(enseignantNom + " enseigne deja "
                    + (c.getMatiere() != null ? c.getMatiere().getNom() : "un cours")
                    + (c.getClasse() != null ? " (" + c.getClasse().getNom() + ")" : "")
                    + " de " + c.getHeureDebut() + " a " + c.getHeureFin() + ".");
            }
        }

        Map<String, Object> reponse = new HashMap<>();
        reponse.put("conflit", !messages.isEmpty());
        reponse.put("messages", messages);
        return reponse;
    }

    /** Compare deux noms d'enseignant sans tenir compte de l'ordre nom/prenom ni de la casse. */
    private boolean memeEnseignant(String a, String b) {
        if (a == null || b == null) return false;
        Set<String> motsA = new HashSet<>(Arrays.asList(a.trim().toUpperCase().split("\\s+")));
        Set<String> motsB = new HashSet<>(Arrays.asList(b.trim().toUpperCase().split("\\s+")));
        return !motsA.isEmpty() && motsA.equals(motsB);
    }

    /** Calcule la liste des conflits de salle (chevauchement horaire, meme jour, meme salle). */
    private List<String> trouverConflits(List<CreneauHoraire> creneaux, Long excluId) {
        List<String> conflits = new ArrayList<>();
        Map<String, List<CreneauHoraire>> parJourSalle = creneaux.stream()
            .filter(c -> c.getSalle() != null && !c.getSalle().isBlank())
            .filter(c -> excluId == null || !c.getId().equals(excluId))
            .collect(Collectors.groupingBy(c -> c.getJour() + "|" + c.getSalle()));
        for (List<CreneauHoraire> groupe : parJourSalle.values()) {
            for (int i = 0; i < groupe.size(); i++) {
                for (int j = i + 1; j < groupe.size(); j++) {
                    CreneauHoraire a = groupe.get(i), b = groupe.get(j);
                    if (creneauxSeChevauchent(a, b)) {
                        conflits.add(a.getSalle() + " est double-reservee le " + JOURS[a.getJour()]
                            + " entre " + (a.getHeureDebut().compareTo(b.getHeureDebut()) > 0 ? a.getHeureDebut() : b.getHeureDebut())
                            + " et " + (a.getHeureFin().compareTo(b.getHeureFin()) < 0 ? a.getHeureFin() : b.getHeureFin())
                            + " (" + (a.getClasse() != null ? a.getClasse().getNom() : "?") + " / " + (b.getClasse() != null ? b.getClasse().getNom() : "?") + ")");
                    }
                }
            }
        }
        return conflits;
    }

    /** Deux creneaux se chevauchent s'ils sont le meme jour et que leurs horaires se croisent. */
    private boolean creneauxSeChevauchent(CreneauHoraire a, CreneauHoraire b) {
        if (!Objects.equals(a.getJour(), b.getJour())) return false;
        if (a.getHeureDebut() == null || a.getHeureFin() == null || b.getHeureDebut() == null || b.getHeureFin() == null) return false;
        return a.getHeureDebut().compareTo(b.getHeureFin()) < 0 && b.getHeureDebut().compareTo(a.getHeureFin()) < 0;
    }

    @PostMapping
    public String ajouter(
            @RequestParam Integer jour,
            @RequestParam String heureDebut,
            @RequestParam String heureFin,
            @RequestParam Long classeId,
            @RequestParam Long matiereId,
            @RequestParam(required = false) String enseignantNom,
            @RequestParam(required = false) String salle,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Classe classe = classeRepository.findById(classeId).orElseThrow();
        Matiere matiere = matiereRepository.findById(matiereId).orElseThrow();
        if (etabId == null || !etabId.equals(classe.getEtablissementId()) || !etabId.equals(matiere.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Classe ou matière introuvable dans cet établissement.");
        }
        if (heureDebut.compareTo(heureFin) >= 0) {
            ra.addFlashAttribute("erreur", "L'heure de fin doit être postérieure à l'heure de début.");
            return "redirect:/emploi-du-temps?classeId=" + classeId;
        }
        CreneauHoraire c = new CreneauHoraire();
        c.setJour(jour);
        c.setHeureDebut(heureDebut);
        c.setHeureFin(heureFin);
        c.setClasse(classe);
        c.setMatiere(matiere);
        c.setEnseignantNom(enseignantNom);
        c.setSalle(salle);
        c.setEtablissementId(etabId);
        creneauRepository.save(c);
        ra.addFlashAttribute("success", "Créneau ajouté.");
        return "redirect:/emploi-du-temps?classeId=" + classeId;
    }

    @PostMapping("/{id}/modifier")
    public String modifier(
            @PathVariable Long id,
            @RequestParam Integer jour,
            @RequestParam String heureDebut,
            @RequestParam String heureFin,
            @RequestParam Long classeId,
            @RequestParam Long matiereId,
            @RequestParam(required = false) String enseignantNom,
            @RequestParam(required = false) String salle,
            RedirectAttributes ra) {

        if (heureDebut.compareTo(heureFin) >= 0) {
            ra.addFlashAttribute("erreur", "L'heure de fin doit être postérieure à l'heure de début.");
            return "redirect:/emploi-du-temps" + (classeId != 0 ? "?classeId=" + classeId : "");
        }
        Long etabId = etablissementService.getCurrentEtablissementId();
        creneauRepository.findById(id)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .ifPresent(c -> {
                c.setJour(jour);
                c.setHeureDebut(heureDebut);
                c.setHeureFin(heureFin);
                classeRepository.findById(classeId)
                    .filter(cl -> etabId.equals(cl.getEtablissementId()))
                    .ifPresent(c::setClasse);
                matiereRepository.findById(matiereId)
                    .filter(m -> etabId.equals(m.getEtablissementId()))
                    .ifPresent(c::setMatiere);
                c.setEnseignantNom(enseignantNom);
                c.setSalle(salle);
                creneauRepository.save(c);
            });
        ra.addFlashAttribute("success", "Créneau modifié avec succès.");
        return "redirect:/emploi-du-temps" + (classeId != 0 ? "?classeId=" + classeId : "");
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@PathVariable Long id,
                            @RequestParam(required = false) Long classeId,
                            RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        creneauRepository.findById(id)
            .filter(c -> etabId != null && etabId.equals(c.getEtablissementId()))
            .ifPresent(creneauRepository::delete);
        ra.addFlashAttribute("success", "Créneau supprimé.");
        return "redirect:/emploi-du-temps" + (classeId != null ? "?classeId=" + classeId : "");
    }

    // ──────────────────────────────────────────────────────────────
    // Import Excel des creneaux (distinct de l'ajout manuel)
    // ──────────────────────────────────────────────────────────────

    private static final String SESSION_IMPORT_KEY = "importCreneauxValides";

    public static class LigneImportCreneau implements Serializable {
        public String jourTexte;
        public String classeTexte;
        public String matiereTexte;
        public String enseignantNom;
        public String salle;
        public String heureDebut;
        public String heureFin;
        public boolean valide;
        public String statut; // VALIDE, JOUR_INVALIDE, CLASSE_INCONNUE, MATIERE_INCONNUE, HORAIRE_INVALIDE
        // Champs resolus, utilises uniquement si valide == true
        public Integer jour;
        public Long classeId;
        public Long matiereId;
    }

    @PostMapping("/import")
    public String importerApercu(
            @RequestParam MultipartFile fichier,
            @RequestParam(required = false) Long classeId,
            HttpSession session,
            Model model) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        List<Classe> classes = classeRepository.findAll().stream()
            .filter(c -> etabId == null || etabId.equals(c.getEtablissementId())).toList();
        List<Matiere> matieres = matiereRepository.findAll().stream()
            .filter(m -> etabId == null || etabId.equals(m.getEtablissementId())).toList();

        List<LigneImportCreneau> apercu = new ArrayList<>();
        String erreurFichier = null;

        if (fichier == null || fichier.isEmpty()) {
            erreurFichier = "Aucun fichier selectionne.";
        } else {
            try (Workbook wb = WorkbookFactory.create(fichier.getInputStream())) {
                Sheet sheet = wb.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // en-tete : Jour | Classe | Matiere | Enseignant | Salle | Heure debut | Heure fin
                    String jourTxt = texte(row.getCell(0));
                    String classeTxt = texte(row.getCell(1));
                    String matiereTxt = texte(row.getCell(2));
                    String enseignantTxt = texte(row.getCell(3));
                    String salleTxt = texte(row.getCell(4));
                    String heureDebutTxt = texte(row.getCell(5));
                    String heureFinTxt = texte(row.getCell(6));

                    if (isBlank(jourTxt) && isBlank(classeTxt) && isBlank(matiereTxt)) continue; // ligne vide

                    LigneImportCreneau l = new LigneImportCreneau();
                    l.jourTexte = jourTxt;
                    l.classeTexte = classeTxt;
                    l.matiereTexte = matiereTxt;
                    l.enseignantNom = enseignantTxt;
                    l.salle = salleTxt;
                    l.heureDebut = heureDebutTxt;
                    l.heureFin = heureFinTxt;

                    Integer jourResolu = resoudreJour(jourTxt);
                    Classe classeResolue = classes.stream()
                        .filter(c -> c.getNom() != null && c.getNom().equalsIgnoreCase(classeTxt == null ? "" : classeTxt.trim()))
                        .findFirst().orElse(null);
                    Matiere matiereResolue = matieres.stream()
                        .filter(m -> m.getNom() != null && m.getNom().equalsIgnoreCase(matiereTxt == null ? "" : matiereTxt.trim()))
                        .findFirst().orElse(null);
                    boolean horaireValide = estHeureValide(heureDebutTxt) && estHeureValide(heureFinTxt)
                        && heureDebutTxt.compareTo(heureFinTxt) < 0;

                    if (jourResolu == null) {
                        l.valide = false; l.statut = "JOUR_INVALIDE";
                    } else if (classeResolue == null) {
                        l.valide = false; l.statut = "CLASSE_INCONNUE";
                    } else if (matiereResolue == null) {
                        l.valide = false; l.statut = "MATIERE_INCONNUE";
                    } else if (!horaireValide) {
                        l.valide = false; l.statut = "HORAIRE_INVALIDE";
                    } else {
                        l.valide = true; l.statut = "VALIDE";
                        l.jour = jourResolu;
                        l.classeId = classeResolue.getId();
                        l.matiereId = matiereResolue.getId();
                    }
                    apercu.add(l);
                }
            } catch (IOException | RuntimeException ex) {
                erreurFichier = "Fichier illisible. Utilisez un fichier Excel (.xlsx) valide.";
            }
        }

        List<LigneImportCreneau> validesUniquement = apercu.stream().filter(l -> l.valide).collect(Collectors.toList());
        session.setAttribute(SESSION_IMPORT_KEY, validesUniquement);

        // Recharger le contexte complet de la page (grille, stats, etc.) + les infos d'apercu
        model.addAttribute("classeIdImport", classeId);
        model.addAttribute("importFichierNom", fichier != null ? fichier.getOriginalFilename() : null);
        model.addAttribute("importErreurFichier", erreurFichier);
        model.addAttribute("importApercu", apercu);
        model.addAttribute("importTotal", apercu.size());
        model.addAttribute("importPret", validesUniquement.size());
        model.addAttribute("importErreurs", apercu.size() - validesUniquement.size());

        return index(classeId, model);
    }

    @PostMapping("/import/confirmer")
    public String confirmerImport(@RequestParam(required = false) Long classeId, HttpSession session, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();

        @SuppressWarnings("unchecked")
        List<LigneImportCreneau> lignes = (List<LigneImportCreneau>) session.getAttribute(SESSION_IMPORT_KEY);
        int saved = 0;
        if (lignes != null) {
            for (LigneImportCreneau l : lignes) {
                Classe classe = classeRepository.findById(l.classeId).orElse(null);
                Matiere matiere = matiereRepository.findById(l.matiereId).orElse(null);
                if (classe == null || matiere == null) continue;

                CreneauHoraire c = new CreneauHoraire();
                c.setJour(l.jour);
                c.setHeureDebut(l.heureDebut);
                c.setHeureFin(l.heureFin);
                c.setClasse(classe);
                c.setMatiere(matiere);
                c.setEnseignantNom(l.enseignantNom);
                c.setSalle(l.salle);
                c.setEtablissementId(etabId);
                creneauRepository.save(c);
                saved++;
            }
            session.removeAttribute(SESSION_IMPORT_KEY);
        }
        ra.addFlashAttribute("success", saved + " creneau(x) importe(s) avec succes.");
        return "redirect:/emploi-du-temps" + (classeId != null ? "?classeId=" + classeId : "");
    }

    private Integer resoudreJour(String txt) {
        if (txt == null || txt.isBlank()) return null;
        String t = txt.trim();
        try {
            int n = Integer.parseInt(t);
            if (n >= 1 && n <= 6) return n;
        } catch (NumberFormatException ignored) {}
        for (int i = 1; i <= 6; i++) {
            if (JOURS[i].equalsIgnoreCase(t)) return i;
        }
        return null;
    }

    private boolean estHeureValide(String h) {
        if (h == null) return false;
        return h.matches("^([01]\\d|2[0-3]):[0-5]\\d$");
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String texte(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) {
            if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                java.time.LocalTime lt = cell.getLocalDateTimeCellValue().toLocalTime();
                return String.format("%02d:%02d", lt.getHour(), lt.getMinute());
            }
            double v = cell.getNumericCellValue();
            if (v == Math.floor(v)) return String.valueOf((long) v);
            return String.valueOf(v);
        }
        return null;
    }
}
