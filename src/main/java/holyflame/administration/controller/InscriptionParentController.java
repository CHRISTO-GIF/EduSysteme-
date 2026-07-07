package holyflame.administration.controller;

import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.UtilisateurRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequestMapping("/inscription-parent")
public class InscriptionParentController {

    @Autowired private EleveRepository eleveRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @GetMapping
    public String formulaire(Model model) {
        return "inscription-parent";
    }

    @PostMapping
    public String creerCompte(
            @RequestParam String codeAcces,
            @RequestParam String motDePasse,
            @RequestParam String confirmationMotDePasse,
            Model model) {

        String code = codeAcces != null ? codeAcces.trim().toUpperCase() : "";

        if (code.isBlank() || motDePasse == null || motDePasse.length() < 6) {
            model.addAttribute("erreur", "Le code d'acces est requis et le mot de passe doit contenir au moins 6 caracteres.");
            return "inscription-parent";
        }
        if (!motDePasse.equals(confirmationMotDePasse)) {
            model.addAttribute("erreur", "Les deux mots de passe ne correspondent pas.");
            return "inscription-parent";
        }

        Optional<Eleve> eleveOpt = eleveRepository.findByPereCodeAccesOrMereCodeAcces(code);
        if (eleveOpt.isEmpty()) {
            model.addAttribute("erreur", "Ce code d'acces est invalide. Contactez le secretariat de l'etablissement.");
            return "inscription-parent";
        }

        Eleve eleve = eleveOpt.get();
        boolean estPere = code.equals(eleve.getPereCodeAcces());
        String email = estPere ? eleve.getPereEmail() : eleve.getMereEmail();
        String nomComplet = estPere ? eleve.getPereNom() : eleve.getMereNom();

        if (email == null || email.isBlank()) {
            model.addAttribute("erreur", "Aucun email n'est associe a ce code d'acces. Contactez le secretariat.");
            return "inscription-parent";
        }
        if (utilisateurRepository.findByEmail(email).isPresent()) {
            model.addAttribute("erreur", "Un compte existe deja pour cet email (" + email + "). Connectez-vous directement depuis la page de connexion.");
            return "inscription-parent";
        }

        String[] parts = nomComplet != null ? nomComplet.trim().split("\\s+", 2) : new String[0];
        String prenom = parts.length > 1 ? parts[0] : "";
        String nom = parts.length > 1 ? parts[1] : (parts.length == 1 ? parts[0] : "Parent");

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setNom(nom);
        utilisateur.setPrenom(prenom);
        utilisateur.setEmail(email);
        utilisateur.setMotDePasse(passwordEncoder.encode(motDePasse));
        utilisateur.setRole("PARENT");
        utilisateur.setActif(true);
        if (eleve.getEtablissementId() != null) {
            Etablissement etab = etablissementRepository.findById(eleve.getEtablissementId()).orElse(null);
            utilisateur.setEtablissement(etab);
        }
        utilisateurRepository.save(utilisateur);

        model.addAttribute("succes", "Votre compte a ete cree avec succes. Vous pouvez maintenant vous connecter avec l'email " + email + ".");
        return "inscription-parent";
    }
}
