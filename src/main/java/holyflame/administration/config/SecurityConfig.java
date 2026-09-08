package holyflame.administration.config;

import holyflame.administration.service.UtilisateurDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomAuthenticationSuccessHandler successHandler;

    @Autowired
    private CustomAuthenticationFailureHandler failureHandler;

    @Autowired
    private UtilisateurDetailsService utilisateurDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html",
                    // Site vitrine du college Holy Flame, deplace ici quand la racine est devenue
                    // la page produit : il reste consultable publiquement, comme avant.
                    "/holyflame.html",
                    // Creation d'un etablissement en un seul ecran : meme porte d'entree publique
                    // que l'assistant detaille /inscription-ecole, dont elle est la version courte.
                    "/demarrer",
                    "/login", "/error", "/inscription-ecole", "/inscription-ecole/**",
                    "/inscription-parent", "/inscription-parent/**",
                    "/mot-de-passe-oublie", "/reinitialiser-mot-de-passe",
                    // Webhook CinetPay : appele serveur a serveur par CinetPay, sans session
                    // utilisateur. Aucune donnee sensible n'est lue depuis la requete elle-meme
                    // (voir PaiementMobileWebhookController) — seul un identifiant de transaction
                    // est accepte, et le vrai statut est toujours revérifié aupres de CinetPay.
                    "/paiements/mobile/notification",
                    // Site vitrine public d'un etablissement (active depuis l'espace MARKETING) —
                    // accessible sans compte, comme n'importe quel site web d'ecole.
                    "/ecole/**",
                    "/h2-console/**", "/css/**", "/js/**", "/fonts/**", "/images/**", "/uploads/**", "/webjars/**", "/assets/**").permitAll()
                .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
                // Le tresorier inscrit lui-meme un eleve : sans eleve en base aucun encaissement
                // n'est possible, et une famille qui se presente pour payer repartait sans recu
                // tant que le secretariat n'avait pas saisi l'inscription. Consultation et creation
                // seulement — modifier ou supprimer un dossier reste au secretariat.
                .requestMatchers(HttpMethod.GET, "/secretariat").hasAnyRole("ADMIN", "SECRETAIRE", "TRESORIER")
                .requestMatchers(HttpMethod.POST, "/secretariat/eleves").hasAnyRole("ADMIN", "SECRETAIRE", "TRESORIER")
                .requestMatchers("/secretariat/**").hasAnyRole("ADMIN", "SECRETAIRE")
                .requestMatchers("/passage/**").hasAnyRole("ADMIN", "SECRETAIRE", "COORDONNATEUR")
                // Les frais de scolarite quittent Parametres, qui est reserve a l'ADMIN : c'est
                // le travail quotidien de la comptable, et les tarifs changent en cours d'annee.
                .requestMatchers("/frais/**").hasAnyRole("ADMIN", "TRESORIER")
                // Grand livre et balance : documents de lecture, sans effet sur les donnees.
                .requestMatchers("/comptabilite/**").hasAnyRole("ADMIN", "TRESORIER")
                // Remises et echeanciers : suivi financier des familles, coeur du poste comptable.
                .requestMatchers("/suivi-familles/**").hasAnyRole("ADMIN", "TRESORIER")
                .requestMatchers("/tresorerie/**").hasAnyRole("ADMIN", "TRESORIER")
                .requestMatchers("/gestion-academique/**").hasRole("ADMIN")
                // Structure academique universitaire (facultes/departements/filieres/UE) :
                // ADMIN gere tout, COORDONNATEUR gere la maquette de sa filiere (cf. cahier des charges module universite)
                .requestMatchers("/academique-universite/**").hasAnyRole("ADMIN", "COORDONNATEUR")
                .requestMatchers("/gestion-classes/**").hasRole("ADMIN")
                .requestMatchers("/gestion-salles/**").hasRole("ADMIN")
                .requestMatchers("/matieres/**").hasRole("ADMIN")
                // Le secretariat peut enregistrer un nouveau personnel (creation uniquement,
                // pas de modification/suppression/documents/comptes des fiches existantes)
                .requestMatchers("/personnel/nouveau", "/personnel/nouveau/**").hasAnyRole("ADMIN", "SECRETAIRE")
                // Le tresorier et le secretariat consultent les fiches personnel (lecture seule),
                // sans acces a la modification/documents/comptes du personnel
                .requestMatchers(HttpMethod.GET, "/personnel", "/personnel/*").hasAnyRole("ADMIN", "ENSEIGNANT", "TRESORIER", "SECRETAIRE", "COORDONNATEUR")
                .requestMatchers("/personnel/**").hasRole("ADMIN")
                // Le surveillant peut signaler/consulter des absences, mais pas gerer les programmes de cours
                .requestMatchers("/surveillance/programmes/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers("/surveillance", "/surveillance/absences/**").hasAnyRole("ADMIN", "ENSEIGNANT", "SURVEILLANT")
                .requestMatchers("/surveillance/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers("/surveillant/**").hasAnyRole("ADMIN", "SURVEILLANT")
                .requestMatchers("/infirmerie/**").hasAnyRole("ADMIN", "INFIRMIER")
                .requestMatchers("/notes/**").hasAnyRole("ADMIN", "ENSEIGNANT", "SECRETAIRE")
                .requestMatchers("/examens/**").hasAnyRole("ADMIN", "ENSEIGNANT", "SECRETAIRE")
                .requestMatchers("/bulletins/**").hasAnyRole("ADMIN", "ENSEIGNANT", "SECRETAIRE", "PARENT")
                .requestMatchers("/portail-parent/**").hasAnyRole("ADMIN", "PARENT")
                .requestMatchers("/messagerie/**").hasAnyRole("ADMIN", "SECRETAIRE", "ENSEIGNANT", "TRESORIER", "COORDONNATEUR")
                // Chaque export est restreint aux roles qui l'utilisent reellement dans l'interface :
                // les paiements restent reserves a la tresorerie, mais notes/eleves sont aussi
                // utilises depuis les pages Examens (enseignant) et Secretariat (secretaire).
                .requestMatchers("/export/paiements/excel").hasAnyRole("ADMIN", "TRESORIER")
                .requestMatchers("/export/eleves/excel").hasAnyRole("ADMIN", "TRESORIER", "SECRETAIRE")
                .requestMatchers("/export/notes/excel").hasAnyRole("ADMIN", "ENSEIGNANT", "SECRETAIRE")
                .requestMatchers("/export/**").hasAnyRole("ADMIN", "TRESORIER")
                .requestMatchers("/parametres/**").hasRole("ADMIN")
                .requestMatchers("/tableau-enseignant/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers("/tableau-eleve/**").hasAnyRole("ADMIN", "ELEVE")
                .requestMatchers("/budget/**").hasAnyRole("ADMIN", "TRESORIER")
                .requestMatchers("/inventaire/**").hasRole("ADMIN")
                // Le versement des salaires est un acte de tresorerie ; contrats/conges restent reserves a l'ADMIN
                .requestMatchers("/rh/salaires/**").hasAnyRole("ADMIN", "TRESORIER")
                // Auto-service : chaque membre du personnel consulte uniquement ses propres bulletins
                // (fiche deduite de son compte connecte, jamais transmise par le client)
                .requestMatchers("/rh/mes-bulletins/**").hasAnyRole(
                    "ADMIN", "ENSEIGNANT", "SECRETAIRE", "TRESORIER", "COORDONNATEUR", "SURVEILLANT", "INFIRMIER", "MARKETING")
                // Un enseignant peut demander/annuler son propre conge (fiche deduite de son compte,
                // jamais transmise par le client) ; l'approbation reste reservee a l'ADMIN
                .requestMatchers("/rh/conges/demander", "/rh/conges/*/annuler").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers("/rh/**").hasRole("ADMIN")
                .requestMatchers("/communication/**").hasAnyRole("ADMIN", "SECRETAIRE")
                // /direction/** ouvert à tout authentifié — contrôle fin dans le controller
                .requestMatchers("/direction/**").authenticated()
                .requestMatchers("/archives/**").hasAnyRole("ADMIN", "SECRETAIRE")
                .requestMatchers("/publications/**").hasAnyRole("ADMIN", "SECRETAIRE")
                .requestMatchers("/portail/**").hasAnyRole("ADMIN", "ELEVE", "SECRETAIRE")
                // Le secretariat peut consulter/renvoyer le recu d'un paiement qu'il vient d'enregistrer
                // (ex: frais d'inscription lors de la creation d'un eleve), sans acces au reste du module Finances
                .requestMatchers("/finances/paiements/*/recu", "/finances/paiements/*/renvoyer-email")
                    .hasAnyRole("ADMIN", "TRESORIER", "SECRETAIRE")
                .requestMatchers("/finances/**").hasAnyRole("ADMIN", "TRESORIER")
                .requestMatchers("/coordination/**").hasAnyRole("ADMIN", "COORDONNATEUR")
                // Espace MARKETING : pilote le site vitrine public de l'etablissement
                // (actualites, galerie, evenements, page a propos) — n'a acces a aucune donnee
                // d'eleve, de personnel ou de finances.
                .requestMatchers("/marketing/**").hasAnyRole("ADMIN", "MARKETING")
                // Assistant conversationnel : ouvert a tous les roles rattaches a un
                // etablissement, SAUF ELEVE. Chaque role ne voit que le sous-ensemble d'outils
                // (donc de donnees) que ce role peut deja consulter ailleurs dans l'appli — voir
                // la map OUTILS_PAR_ROLE dans AssistantService. Le PARENT a ses propres outils,
                // strictement limites a ses enfants (jamais l'etablissement entier).
                .requestMatchers("/assistant/**").hasAnyRole(
                    "ADMIN", "ENSEIGNANT", "SECRETAIRE", "TRESORIER", "COORDONNATEUR",
                    "SURVEILLANT", "INFIRMIER", "PARENT", "SUPER_ADMIN", "MARKETING")
                // Journal d'activite : reserve au personnel (chacun n'y voit que ses propres actions,
                // sauf ADMIN qui voit tout) — un eleve ou un parent n'a aucune raison d'y acceder.
                .requestMatchers("/journal/**").hasAnyRole("ADMIN", "ENSEIGNANT", "SECRETAIRE", "TRESORIER", "COORDONNATEUR", "SURVEILLANT", "INFIRMIER", "MARKETING")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .rememberMe(rm -> rm
                .key("holyflame-edusystem-remember-me-key")
                .tokenValiditySeconds(14 * 24 * 60 * 60) // 14 jours
                .userDetailsService(utilisateurDetailsService)
                .rememberMeParameter("remember-me")
            )
            .headers(h -> h.frameOptions(fo -> fo.sameOrigin()))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/paiements/mobile/notification"))
            .addFilterAfter(new CsrfTokenEagerLoadFilter(), CsrfFilter.class)
            .addFilterAfter(new FontPreloadFilter(), CsrfTokenEagerLoadFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
