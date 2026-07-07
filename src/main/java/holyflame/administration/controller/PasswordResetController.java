package holyflame.administration.controller;

import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

@Controller
public class PasswordResetController {

    private static final int VALIDITE_HEURES = 2;

    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @GetMapping("/mot-de-passe-oublie")
    public String demandeForm() {
        return "mot-de-passe-oublie";
    }

    @PostMapping("/mot-de-passe-oublie")
    public String demandeSoumettre(@RequestParam String email, Model model) {
        model.addAttribute("email", email);

        utilisateurRepository.findByEmail(email.trim()).ifPresent(u -> {
            String token = UUID.randomUUID().toString();
            u.setResetToken(token);
            u.setResetTokenExpiration(LocalDateTime.now().plusHours(VALIDITE_HEURES));
            utilisateurRepository.save(u);
            model.addAttribute("lienReinitialisation", "/reinitialiser-mot-de-passe?token=" + token);
        });

        // Message générique dans tous les cas (on ne révèle pas si l'email existe ou non)
        model.addAttribute("demandeEnvoyee", true);
        return "mot-de-passe-oublie";
    }

    @GetMapping("/reinitialiser-mot-de-passe")
    public String reinitialiserForm(@RequestParam String token, Model model) {
        Utilisateur u = utilisateurRepository.findByResetToken(token).orElse(null);
        if (u == null || u.getResetTokenExpiration() == null || u.getResetTokenExpiration().isBefore(LocalDateTime.now())) {
            model.addAttribute("tokenInvalide", true);
        } else {
            model.addAttribute("token", token);
        }
        return "reinitialiser-mot-de-passe";
    }

    @PostMapping("/reinitialiser-mot-de-passe")
    public String reinitialiserSoumettre(
            @RequestParam String token,
            @RequestParam String motDePasse,
            @RequestParam String confirmationMotDePasse,
            Model model,
            RedirectAttributes ra) {

        Utilisateur u = utilisateurRepository.findByResetToken(token).orElse(null);
        if (u == null || u.getResetTokenExpiration() == null || u.getResetTokenExpiration().isBefore(LocalDateTime.now())) {
            model.addAttribute("tokenInvalide", true);
            return "reinitialiser-mot-de-passe";
        }
        if (motDePasse == null || motDePasse.length() < 6) {
            model.addAttribute("token", token);
            model.addAttribute("erreur", "Le mot de passe doit contenir au moins 6 caracteres.");
            return "reinitialiser-mot-de-passe";
        }
        if (!motDePasse.equals(confirmationMotDePasse)) {
            model.addAttribute("token", token);
            model.addAttribute("erreur", "Les mots de passe ne correspondent pas.");
            return "reinitialiser-mot-de-passe";
        }

        u.setMotDePasse(passwordEncoder.encode(motDePasse));
        u.setResetToken(null);
        u.setResetTokenExpiration(null);
        utilisateurRepository.save(u);

        ra.addFlashAttribute("motDePasseReinitialise", true);
        return "redirect:/login";
    }
}
