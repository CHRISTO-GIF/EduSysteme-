package holyflame.administration.controller;

import holyflame.administration.model.Matiere;
import holyflame.administration.repository.CreneauHoraireRepository;
import holyflame.administration.repository.MatiereRepository;
import holyflame.administration.repository.NoteRepository;
import holyflame.administration.service.EtablissementService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/matieres")
public class MatiereController {

    private static final Set<String> POLES_VALIDES = Set.of("SCIENTIFIQUE", "LITTERAIRE", "ARTS_SPORT", "AUTRE");

    @Autowired private MatiereRepository matiereRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private CreneauHoraireRepository creneauHoraireRepository;
    @Autowired private EtablissementService etablissementService;

    @GetMapping
    public String index(@RequestParam(required = false, defaultValue = "manuelle") String tab, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabId));
        model.addAttribute("activeTab", tab);
        return "matieres";
    }

    @PostMapping
    public String ajouterMatiere(
            @RequestParam String nom,
            @RequestParam(required = false) Double coefficient,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String pole,
            RedirectAttributes ra) {

        if (coefficient != null && coefficient <= 0) {
            ra.addFlashAttribute("erreur", "Le coefficient doit être supérieur à 0.");
            return "redirect:/matieres";
        }
        Long etabIdAjout = etablissementService.getCurrentEtablissementId();
        if (matiereRepository.findByNomIgnoreCaseAndEtablissementId(nom.trim(), etabIdAjout).isPresent()) {
            ra.addFlashAttribute("erreur", "Une matière nommée \"" + nom.trim() + "\" existe déjà.");
            return "redirect:/matieres";
        }
        Matiere matiere = new Matiere();
        matiere.setNom(nom);
        matiere.setCoefficient(coefficient != null ? coefficient : 1.0);
        matiere.setDescription(description);
        matiere.setPole(pole != null && !pole.isBlank() ? pole : "AUTRE");
        matiere.setEtablissementId(etablissementService.getCurrentEtablissementId());
        matiereRepository.save(matiere);
        return "redirect:/matieres";
    }

    @PostMapping("/{id}/modifier")
    public String modifierMatiere(
            @PathVariable Long id,
            @RequestParam String nom,
            @RequestParam(required = false) Double coefficient,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String pole,
            RedirectAttributes ra) {

        Long etabId = etablissementService.getCurrentEtablissementId();
        Matiere matiere = matiereRepository.findById(id).orElseThrow();
        if (matiere.getEtablissementId() != null) verifierProprietaire(matiere, etabId);
        if (coefficient == null || coefficient <= 0) {
            ra.addFlashAttribute("erreur", "Le coefficient doit être supérieur à 0.");
            return "redirect:/matieres";
        }
        boolean nomDejaPris = matiereRepository.findByNomIgnoreCaseAndEtablissementId(nom.trim(), etabId)
            .map(autre -> !autre.getId().equals(id))
            .orElse(false);
        if (nomDejaPris) {
            ra.addFlashAttribute("erreur", "Une matière nommée \"" + nom.trim() + "\" existe déjà.");
            return "redirect:/matieres";
        }
        matiere.setNom(nom);
        matiere.setCoefficient(coefficient);
        matiere.setDescription(description);
        matiere.setPole(pole != null && !pole.isBlank() ? pole : "AUTRE");
        if (matiere.getEtablissementId() == null) {
            matiere.setEtablissementId(etabId);
        }
        matiereRepository.save(matiere);
        return "redirect:/matieres";
    }

    @Transactional
    @PostMapping("/{id}/supprimer")
    public String supprimerMatiere(@PathVariable Long id, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Matiere matiere = matiereRepository.findById(id).orElseThrow();
        if (matiere.getEtablissementId() != null) verifierProprietaire(matiere, etabId);
        long nbNotes = noteRepository.countByMatiereId(id);
        if (nbNotes > 0) {
            ra.addFlashAttribute("erreur",
                "Impossible de supprimer \"" + matiere.getNom() + "\" : " + nbNotes
                + " note(s) y sont associées. Supprimez-les d'abord depuis la saisie de notes.");
            return "redirect:/matieres";
        }
        creneauHoraireRepository.deleteByMatiereId(id);
        matiereRepository.deleteById(id);
        ra.addFlashAttribute("success", "Matière supprimée avec succès.");
        return "redirect:/matieres";
    }

    // ──────────────────────────────────────────────────────────────
    // Import Excel des matières
    // ──────────────────────────────────────────────────────────────

    private static final String SESSION_IMPORT_KEY = "importMatieresValides";

    public static class LigneImportMatiere implements Serializable {
        public String nom;
        public Double coefficient;
        public String pole;
        public String description;
        public boolean valide;
        public String statut; // VALIDE, NOM_MANQUANT, COEFFICIENT_INVALIDE, DOUBLON
    }

    @GetMapping("/import/modele")
    public void telechargerModele(HttpServletResponse response) throws IOException {
        XSSFWorkbook wb = new XSSFWorkbook();
        var sheet = wb.createSheet("Matieres");

        XSSFCellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0, (byte) 35, (byte) 111}, null));
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont headerFont = wb.createFont();
        headerFont.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        String[] entetes = {"Nom", "Coefficient", "Pole (SCIENTIFIQUE, LITTERAIRE, ARTS_SPORT, AUTRE)", "Description"};
        Row ligneEntete = sheet.createRow(0);
        for (int i = 0; i < entetes.length; i++) {
            Cell c = ligneEntete.createCell(i);
            c.setCellValue(entetes[i]);
            c.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, 22 * 256);
        }

        Row exemple = sheet.createRow(1);
        exemple.createCell(0).setCellValue("Mathématiques");
        exemple.createCell(1).setCellValue(2.0);
        exemple.createCell(2).setCellValue("SCIENTIFIQUE");
        exemple.createCell(3).setCellValue("Algèbre, géométrie, analyse");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"modele-import-matieres.xlsx\"");
        wb.write(response.getOutputStream());
        wb.close();
    }

    @PostMapping("/import")
    public String importerApercu(@RequestParam MultipartFile fichier, HttpSession session, Model model) {
        Long etabId = etablissementService.getCurrentEtablissementId();
        Set<String> nomsExistants = matiereRepository.findByEtablissementIdOrderByNomAsc(etabId).stream()
            .map(m -> m.getNom().trim().toLowerCase())
            .collect(Collectors.toCollection(HashSet::new));

        List<LigneImportMatiere> apercu = new ArrayList<>();
        String erreurFichier = null;
        if (fichier == null || fichier.isEmpty()) {
            erreurFichier = "Aucun fichier sélectionné.";
        } else {
            try (Workbook wb = WorkbookFactory.create(fichier.getInputStream())) {
                Sheet sheet = wb.getSheetAt(0);
                Set<String> nomsVus = new HashSet<>(nomsExistants);
                for (Row row : sheet) {
                    if (row.getRowNum() == 0) continue; // en-tête
                    String nom = lireCelluleTexte(row.getCell(0));
                    Double coefficient = lireCelluleNombre(row.getCell(1));
                    String pole = lireCelluleTexte(row.getCell(2));
                    String description = lireCelluleTexte(row.getCell(3));
                    if ((nom == null || nom.isBlank()) && coefficient == null && (pole == null || pole.isBlank())) continue; // ligne vide

                    LigneImportMatiere ligne = new LigneImportMatiere();
                    ligne.nom = nom;
                    ligne.coefficient = coefficient != null ? coefficient : 1.0;
                    String poleNormalise = pole != null ? pole.trim().toUpperCase() : "";
                    ligne.pole = POLES_VALIDES.contains(poleNormalise) ? poleNormalise : "AUTRE";
                    ligne.description = description;

                    if (nom == null || nom.isBlank()) {
                        ligne.valide = false;
                        ligne.statut = "NOM_MANQUANT";
                    } else if (coefficient != null && coefficient <= 0) {
                        ligne.valide = false;
                        ligne.statut = "COEFFICIENT_INVALIDE";
                    } else if (nomsVus.contains(nom.trim().toLowerCase())) {
                        ligne.valide = false;
                        ligne.statut = "DOUBLON";
                    } else {
                        ligne.valide = true;
                        ligne.statut = "VALIDE";
                        nomsVus.add(nom.trim().toLowerCase());
                    }
                    apercu.add(ligne);
                }
            } catch (IOException | RuntimeException ex) {
                erreurFichier = "Fichier illisible. Utilisez un fichier Excel (.xlsx ou .xls) valide.";
            }
        }

        List<LigneImportMatiere> validesUniquement = apercu.stream().filter(l -> l.valide).collect(Collectors.toList());
        session.setAttribute(SESSION_IMPORT_KEY, validesUniquement);

        Long etabIdModel = etablissementService.getCurrentEtablissementId();
        model.addAttribute("utilisateurConnecte", etablissementService.getCurrentUtilisateur());
        model.addAttribute("matieres", matiereRepository.findByEtablissementIdOrderByNomAsc(etabIdModel));
        model.addAttribute("activeTab", "import");
        model.addAttribute("erreurImport", erreurFichier);
        model.addAttribute("importFichierNom", fichier != null ? fichier.getOriginalFilename() : null);
        model.addAttribute("importApercu", apercu);
        model.addAttribute("importTotal", apercu.size());
        model.addAttribute("importPret", validesUniquement.size());
        model.addAttribute("importErreurs", apercu.size() - validesUniquement.size());
        return "matieres";
    }

    @PostMapping("/import/confirmer")
    public String confirmerImport(HttpSession session, RedirectAttributes ra) {
        Long etabId = etablissementService.getCurrentEtablissementId();

        @SuppressWarnings("unchecked")
        List<LigneImportMatiere> lignes = (List<LigneImportMatiere>) session.getAttribute(SESSION_IMPORT_KEY);
        int saved = 0;
        if (lignes != null) {
            for (LigneImportMatiere l : lignes) {
                Matiere matiere = new Matiere();
                matiere.setNom(l.nom.trim());
                matiere.setCoefficient(l.coefficient);
                matiere.setPole(l.pole);
                matiere.setDescription(l.description);
                matiere.setEtablissementId(etabId);
                matiereRepository.save(matiere);
                saved++;
            }
            session.removeAttribute(SESSION_IMPORT_KEY);
        }
        ra.addFlashAttribute("success", saved + " matière(s) importée(s) avec succès.");
        return "redirect:/matieres?tab=manuelle";
    }

    private String lireCelluleTexte(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.STRING) return cell.getStringCellValue().trim();
        if (cell.getCellType() == CellType.NUMERIC) return String.valueOf((long) cell.getNumericCellValue());
        return null;
    }

    private Double lireCelluleNombre(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC) return cell.getNumericCellValue();
            if (cell.getCellType() == CellType.STRING) return Double.parseDouble(cell.getStringCellValue().trim().replace(",", "."));
        } catch (Exception ignored) {}
        return null;
    }

    private void verifierProprietaire(Matiere matiere, Long etabId) {
        if (etabId == null || !etabId.equals(matiere.getEtablissementId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "Matière introuvable dans cet établissement.");
        }
    }
}
