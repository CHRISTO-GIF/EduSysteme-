package holyflame.administration.controller;

import holyflame.administration.model.Eleve;
import holyflame.administration.model.Etablissement;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.EtablissementRepository;
import holyflame.administration.repository.UtilisateurRepository;
import holyflame.administration.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/inscription-parent")
public class InscriptionParentController {

    /** Duree de validite du lien de confirmation envoye par email. */
    private static final int VALIDITE_HEURES = 24;

    @Autowired private EleveRepository eleveRepository;
    @Autowired private UtilisateurRepository utilisateurRepository;
    @Autowired private EtablissementRepository etablissementRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;

    @Value("${app.mail.base-url}") private String baseUrl;

    @GetMapping
    public String formulaire(Model model) {
        return "inscription-parent";
    }

    /**
     * Cree le compte parent en attente de confirmation (actif = false) et envoie un lien
     * de confirmation par email — meme mecanisme (token + expiration) que "mot de passe oublie",
     * pour s'assurer que le demandeur possede reellement la boite mail associee au code d'acces
     * avant d'activer un acces aux notes/absences/finances de l'enfant.
     */
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

        String[] parts = nomComplet != null ? nomComplet.trim().split("\\s+", 2) : new String[0];
        String prenom = parts.length > 1 ? parts[0] : "";
        String nom = parts.length > 1 ? parts[1] : (parts.length == 1 ? parts[0] : "Parent");

        Optional<Utilisateur> compteExistant = utilisateurRepository.findByEmail(email);
        Utilisateur utilisateur;
        if (compteExistant.isPresent()) {
            utilisateur = compteExistant.get();
            if (utilisateur.isActif()) {
                model.addAttribute("erreur", "Un compte existe deja pour cet email. Connectez-vous directement depuis la page de connexion.");
                return "inscription-parent";
            }
            // Compte deja cree mais jamais confirme (email precedent non recu/expire) :
            // on renouvelle le mot de passe choisi et on renvoie un nouveau lien plutot que de bloquer.
            utilisateur.setMotDePasse(passwordEncoder.encode(motDePasse));
        } else {
            utilisateur = new Utilisateur();
            utilisateur.setNom(nom);
            utilisateur.setPrenom(prenom);
            utilisateur.setEmail(email);
            utilisateur.setMotDePasse(passwordEncoder.encode(motDePasse));
            utilisateur.setRole("PARENT");
            utilisateur.setActif(false);
            if (eleve.getEtablissementId() != null) {
                Etablissement etab = etablissementRepository.findById(eleve.getEtablissementId()).orElse(null);
                utilisateur.setEtablissement(etab);
            }
        }

        String token = UUID.randomUUID().toString();
        utilisateur.setResetToken(token);
        utilisateur.setResetTokenExpiration(LocalDateTime.now().plusHours(VALIDITE_HEURES));
        utilisateurRepository.save(utilisateur);

        String lien = baseUrl + "/inscription-parent/confirmer?token=" + token;
        String corpsHtml = "<p>Bonjour" + (prenom.isBlank() ? "" : " " + prenom) + ",</p>"
            + "<p>Une demande de creation de compte parent EduSystem Pro a ete faite avec cette adresse email.</p>"
            + "<p><a href=\"" + lien + "\">Cliquez ici pour confirmer et activer votre compte</a></p>"
            + "<p>Ce lien est valable " + VALIDITE_HEURES + " heures. Si vous n'etes pas a l'origine de cette demande, ignorez cet email : aucun compte ne sera active.</p>";
        boolean envoye = emailService.envoyer(email, "Confirmez votre compte parent EduSystem Pro", corpsHtml);

        model.addAttribute("succes", "Verifiez votre boite mail (" + email + ") et cliquez sur le lien de confirmation pour activer votre compte.");
        if (!envoye) {
            model.addAttribute("lienConfirmation", lien);
        }
        return "inscription-parent";
    }

    /** Active le compte apres clic sur le lien de confirmation recu par email. */
    @GetMapping("/confirmer")
    public String confirmer(@RequestParam String token, Model model) {
        Utilisateur utilisateur = utilisateurRepository.findByResetToken(token).orElse(null);
        if (utilisateur == null || utilisateur.getResetTokenExpiration() == null
                || utilisateur.getResetTokenExpiration().isBefore(LocalDateTime.now())) {
            model.addAttribute("erreur", "Ce lien de confirmation est invalide ou a expire. Recommencez l'inscription avec votre code d'acces.");
            return "inscription-parent";
        }
        utilisateur.setActif(true);
        utilisateur.setResetToken(null);
        utilisateur.setResetTokenExpiration(null);
        utilisateurRepository.save(utilisateur);
        model.addAttribute("succes", "Votre compte est confirme et actif. Vous pouvez maintenant vous connecter avec l'email " + utilisateur.getEmail() + ".");
        return "inscription-parent";
    }
}
