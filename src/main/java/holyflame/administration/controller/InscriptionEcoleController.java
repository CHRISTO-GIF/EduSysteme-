package holyflame.administration.controller;

import holyflame.administration.model.Classe;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.FileStorageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Controller
public class InscriptionEcoleController {

    private static final String SESSION_KEY = "inscriptionEcoleDonnees";

    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private FileStorageService fileStorageService;

    /** Code de niveau -> [nom affiche, cycle]. Utilise pour generer une classe par defaut par niveau coche. */
    private static final java.util.Map<String, String[]> NIVEAUX = new java.util.LinkedHashMap<>();
    static {
        NIVEAUX.put("MATERNELLE_PS", new String[]{"Petite Section", "Maternelle"});
        NIVEAUX.put("MATERNELLE_MS", new String[]{"Moyenne Section", "Maternelle"});
        NIVEAUX.put("MATERNELLE_GS", new String[]{"Grande Section", "Maternelle"});
        NIVEAUX.put("PRIMAIRE_CP1", new String[]{"CP1", "Primaire"});
        NIVEAUX.put("PRIMAIRE_CP2", new String[]{"CP2", "Primaire"});
        NIVEAUX.put("PRIMAIRE_CE1", new String[]{"CE1", "Primaire"});
        NIVEAUX.put("PRIMAIRE_CE2", new String[]{"CE2", "Primaire"});
        NIVEAUX.put("PRIMAIRE_CM1", new String[]{"CM1", "Primaire"});
        NIVEAUX.put("PRIMAIRE_CM2", new String[]{"CM2", "Primaire"});
        NIVEAUX.put("COLLEGE_6E", new String[]{"6ème", "Collège"});
        NIVEAUX.put("COLLEGE_5E", new String[]{"5ème", "Collège"});
        NIVEAUX.put("COLLEGE_4E", new String[]{"4ème", "Collège"});
        NIVEAUX.put("COLLEGE_3E", new String[]{"3ème", "Collège"});
        NIVEAUX.put("LYCEE_SECONDE", new String[]{"Seconde", "Lycée"});
        NIVEAUX.put("LYCEE_PREMIERE", new String[]{"Première", "Lycée"});
        NIVEAUX.put("LYCEE_TERMINALE", new String[]{"Terminale", "Lycée"});
        NIVEAUX.put("SUPERIEUR_L1", new String[]{"Licence 1", "Supérieur"});
        NIVEAUX.put("SUPERIEUR_L2", new String[]{"Licence 2", "Supérieur"});
        NIVEAUX.put("SUPERIEUR_L3", new String[]{"Licence 3", "Supérieur"});
        NIVEAUX.put("SUPERIEUR_M1", new String[]{"Master 1", "Supérieur"});
        NIVEAUX.put("SUPERIEUR_M2", new String[]{"Master 2", "Supérieur"});
        NIVEAUX.put("SUPERIEUR_DOCTORAT", new String[]{"Doctorat", "Supérieur"});
    }

    public static class DonneesInscription implements Serializable {
        public String nom;
        public String categorie;
        public String niveaux;
        public List<String> niveauxSelectionnes;
        public String adresse;
        public String email;
        public String telephone;
        public String anneeScolaire;
        public LocalDate dateDebutSession;
        public LocalDate dateFinSession;
        public String systemeNotation;
        public Integer seuilAssiduite;
        public String alerteAbsences;
        public String calculRetard;
        public boolean statutsIncompletAbandon;
        public boolean rangAutomatique;
        public String logoPath;
        public String couleurPrimaire;
        public String langueSysteme;
    }

    @GetMapping("/inscription-ecole")
    public String general(Model model, HttpSession session) {
        model.addAttribute("donnees", session.getAttribute(SESSION_KEY));
        return "inscription-ecole-general";
    }

    @PostMapping("/inscription-ecole")
    public String enregistrerGeneral(
            @RequestParam String nom,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) List<String> niveaux,
            @RequestParam(required = false) String adresse,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String telephone,
            HttpSession session,
            Model model) {

        if (nom == null || nom.isBlank()) {
            model.addAttribute("erreur", "Le nom de l'etablissement est obligatoire.");
            return "inscription-ecole-general";
        }

        DonneesInscription donnees = new DonneesInscription();
        donnees.nom = nom.trim();
        donnees.categorie = categorie;
        donnees.niveauxSelectionnes = niveaux;
        // Resume des cycles (ex: "Primaire, Collège") pour l'affichage/le type d'etablissement —
        // la liste detaillee (niveauxSelectionnes) sert a generer les classes reelles dans finaliser().
        donnees.niveaux = niveaux != null
            ? niveaux.stream().map(code -> NIVEAUX.getOrDefault(code, new String[]{code, code})[1])
                .distinct().collect(java.util.stream.Collectors.joining(", "))
            : null;
        donnees.adresse = adresse;
        donnees.email = email;
        donnees.telephone = telephone;
        session.setAttribute(SESSION_KEY, donnees);

        return "redirect:/inscription-ecole/academique";
    }

    @GetMapping("/inscription-ecole/academique")
    public String academique(HttpSession session) {
        if (session.getAttribute(SESSION_KEY) == null) {
            return "redirect:/inscription-ecole";
        }
        return "inscription-ecole-academique";
    }

    @PostMapping("/inscription-ecole/academique")
    public String enregistrerAcademique(
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin,
            @RequestParam(required = false) String systemeNotation,
            @RequestParam(required = false) Integer seuilAssiduite,
            @RequestParam(required = false) String alerteAbsences,
            @RequestParam(required = false) String calculRetard,
            @RequestParam(required = false) Boolean statutsIncompletAbandon,
            @RequestParam(required = false) Boolean rangAutomatique,
            HttpSession session) {

        DonneesInscription donnees = (DonneesInscription) session.getAttribute(SESSION_KEY);
        if (donnees == null) {
            return "redirect:/inscription-ecole";
        }

        try {
            if (dateDebut != null && !dateDebut.isBlank()) {
                donnees.dateDebutSession = LocalDate.parse(dateDebut);
            }
            if (dateFin != null && !dateFin.isBlank()) {
                donnees.dateFinSession = LocalDate.parse(dateFin);
            }
            if (donnees.dateDebutSession != null && donnees.dateFinSession != null) {
                donnees.anneeScolaire = donnees.dateDebutSession.getYear() + "-" + donnees.dateFinSession.getYear();
            }
        } catch (Exception ignored) {
        }

        donnees.systemeNotation = systemeNotation != null ? systemeNotation : "NUMERIQUE";
        donnees.seuilAssiduite = seuilAssiduite != null ? seuilAssiduite : 75;
        donnees.alerteAbsences = alerteAbsences;
        donnees.calculRetard = calculRetard;
        donnees.statutsIncompletAbandon = Boolean.TRUE.equals(statutsIncompletAbandon);
        donnees.rangAutomatique = Boolean.TRUE.equals(rangAutomatique);
        session.setAttribute(SESSION_KEY, donnees);

        return "redirect:/inscription-ecole/branding";
    }

    @GetMapping("/inscription-ecole/branding")
    public String branding(HttpSession session) {
        if (session.getAttribute(SESSION_KEY) == null) {
            return "redirect:/inscription-ecole";
        }
        return "inscription-ecole-branding";
    }

    @PostMapping("/inscription-ecole/branding")
    public String enregistrerBranding(
            @RequestParam(required = false) MultipartFile logo,
            @RequestParam(required = false) String couleurPrimaire,
            @RequestParam(required = false) String langueSysteme,
            HttpSession session) {

        DonneesInscription donnees = (DonneesInscription) session.getAttribute(SESSION_KEY);
        if (donnees == null) {
            return "redirect:/inscription-ecole";
        }

        if (logo != null && !logo.isEmpty()) {
            try {
                donnees.logoPath = fileStorageService.store(logo, "logos");
            } catch (IOException ignored) {
            }
        }
        donnees.couleurPrimaire = couleurPrimaire;
        donnees.langueSysteme = langueSysteme;
        session.setAttribute(SESSION_KEY, donnees);

        return "redirect:/inscription-ecole/utilisateurs";
    }

    @GetMapping("/inscription-ecole/utilisateurs")
    public String utilisateurs(HttpSession session) {
        if (session.getAttribute(SESSION_KEY) == null) {
            return "redirect:/inscription-ecole";
        }
        return "inscription-ecole-utilisateurs";
    }

    @PostMapping("/inscription-ecole/utilisateurs/finaliser")
    public String finaliser(
            @RequestParam String adminNomComplet,
            @RequestParam String adminEmail,
            @RequestParam(required = false) String adminRole,
            HttpSession session,
            Model model,
            RedirectAttributes ra) {

        DonneesInscription donnees = (DonneesInscription) session.getAttribute(SESSION_KEY);
        if (donnees == null) {
            return "redirect:/inscription-ecole";
        }
        if (adminNomComplet == null || adminNomComplet.isBlank() || adminEmail == null || adminEmail.isBlank()) {
            model.addAttribute("erreur", "Le nom et l'email de l'administrateur sont obligatoires.");
            return "inscription-ecole-utilisateurs";
        }
        if (utilisateurRepository.findByEmail(adminEmail.trim()).isPresent()) {
            model.addAttribute("erreur", "Un compte existe deja avec cet email.");
            return "inscription-ecole-utilisateurs";
        }

        // Etablissement
        Etablissement etab = new Etablissement();
        etab.setNom(donnees.nom);
        etab.setAdresse(donnees.adresse);
        etab.setEmail(donnees.email);
        etab.setTelephone(donnees.telephone);
        etab.setTypeEtablissement(donnees.niveaux != null ? donnees.niveaux : donnees.categorie);
        etab.setContact(adminNomComplet.trim());
        etab.setAnneeScolaire(donnees.anneeScolaire != null ? donnees.anneeScolaire : (LocalDate.now().getYear() + "-" + (LocalDate.now().getYear() + 1)));
        etab.setStatut("ACTIF");
        etab.setDateCreation(LocalDate.now());
        String codeAcces = "HF-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        etab.setCodeAcces(codeAcces);

        etab.setDateDebutSession(donnees.dateDebutSession);
        etab.setDateFinSession(donnees.dateFinSession);
        etab.setSystemeNotation(donnees.systemeNotation != null ? donnees.systemeNotation : "NUMERIQUE");
        etab.setSeuilAssiduite(donnees.seuilAssiduite != null ? donnees.seuilAssiduite : 75);
        etab.setAlerteAbsences(donnees.alerteAbsences);
        etab.setCalculRetard(donnees.calculRetard);
        etab.setStatutsIncompletAbandon(donnees.statutsIncompletAbandon);
        etab.setRangAutomatique(donnees.rangAutomatique);
        etab.setLogoPath(donnees.logoPath);
        etab.setCouleurPrimaire(donnees.couleurPrimaire != null ? donnees.couleurPrimaire : "#00236f");
        etab.setLangueSysteme(donnees.langueSysteme != null ? donnees.langueSysteme : "Francais");

        etablissementRepository.save(etab);

        // Classes generees automatiquement a partir des niveaux coches a l'etape 1
        // (une classe "A" par niveau ; l'etablissement pourra ajouter des sections
        // ou des classes speciales ensuite depuis Gestion Academique).
        if (donnees.niveauxSelectionnes != null) {
            for (String code : donnees.niveauxSelectionnes) {
                String[] info = NIVEAUX.get(code);
                if (info == null) continue;
                Classe classe = new Classe();
                classe.setNom(info[0] + " A");
                classe.setNiveau(info[0]);
                classe.setAnneeScolaire(etab.getAnneeScolaire());
                classe.setEtablissementId(etab.getId());
                classeRepository.save(classe);
            }
        }

        // Administrateur
        String[] parts = adminNomComplet.trim().split("\\s+", 2);
        String prenom = parts.length > 1 ? parts[0] : "";
        String nomFamille = parts.length > 1 ? parts[1] : parts[0];
        String role = mapRole(adminRole);
        String motDePasseGenere = genererMotDePasse();

        Utilisateur admin = new Utilisateur();
        admin.setNom(nomFamille.toUpperCase());
        admin.setPrenom(prenom);
        admin.setEmail(adminEmail.trim());
        admin.setMotDePasse(passwordEncoder.encode(motDePasseGenere));
        admin.setRole(role);
        admin.setEtablissement(etab);
        utilisateurRepository.save(admin);

        session.removeAttribute(SESSION_KEY);

        ra.addFlashAttribute("codeAcces", codeAcces);
        ra.addFlashAttribute("motDePasse", motDePasseGenere);
        ra.addFlashAttribute("adminEmail", admin.getEmail());
        ra.addFlashAttribute("nomEcole", etab.getNom());
        return "redirect:/inscription-ecole/confirmation";
    }

    @GetMapping("/inscription-ecole/confirmation")
    public String confirmation(Model model) {
        if (!model.containsAttribute("codeAcces")) {
            return "redirect:/inscription-ecole";
        }
        return "inscription-ecole-confirmation";
    }

    private String mapRole(String adminRole) {
        if (adminRole == null) return "ADMIN";
        return switch (adminRole) {
            case "secretary" -> "SECRETAIRE";
            case "accountant" -> "TRESORIER";
            case "director", "it" -> "ADMIN";
            default -> "ADMIN";
        };
    }

    private String genererMotDePasse() {
        // ThreadLocalRandom plutot que SecureRandom : ce mot de passe est temporaire et
        // affiche immediatement a l'admin pour changement, pas besoin d'aleatoire cryptographique,
        // et SecureRandom peut se bloquer plusieurs secondes en attendant de l'entropie sur certains
        // conteneurs Linux (cause reelle d'un crash observe en production sur cette route).
        String caracteres = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        var random = java.util.concurrent.ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }
        return sb.toString();
    }
}
