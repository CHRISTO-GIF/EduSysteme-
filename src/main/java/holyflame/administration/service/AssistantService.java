package holyflame.administration.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import holyflame.administration.model.Absence;
import holyflame.administration.model.Eleve;
import holyflame.administration.model.MembreEquipePublic;
import holyflame.administration.model.Paiement;
import holyflame.administration.model.Personnel;
import holyflame.administration.model.SiteVitrine;
import holyflame.administration.model.TemoignageSite;
import holyflame.administration.model.Utilisateur;
import holyflame.administration.repository.AbsenceRepository;
import holyflame.administration.repository.ClasseRepository;
import holyflame.administration.repository.EleveRepository;
import holyflame.administration.repository.MembreEquipePublicRepository;
import holyflame.administration.repository.PaiementRepository;
import holyflame.administration.repository.ParametreRepository;
import holyflame.administration.repository.PersonnelRepository;
import holyflame.administration.repository.SiteVitrineRepository;
import holyflame.administration.repository.TemoignageSiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Assistant conversationnel adosse aux vraies donnees de l'etablissement.
 *
 * Regle de securite non negociable : l'etablissement de l'utilisateur (etabId) est
 * capture cote serveur AVANT le premier appel a Claude et jamais fourni par le
 * modele. Les outils n'acceptent en entree que des criteres de recherche (nom,
 * nombre de jours...) — jamais un identifiant d'etablissement ou d'eleve brut —
 * afin qu'une instruction glissee dans la conversation (prompt injection) ne
 * puisse pas faire fuiter les donnees d'un autre etablissement.
 */
@Service
public class AssistantService {

    @Value("${app.anthropic.api-key}")
    private String apiKey;

    @Value("${app.anthropic.model}")
    private String model;

    @Autowired private EleveRepository eleveRepository;
    @Autowired private PersonnelRepository personnelRepository;
    @Autowired private AbsenceRepository absenceRepository;
    @Autowired private ClasseRepository classeRepository;
    @Autowired private PaiementRepository paiementRepository;
    @Autowired private ParametreRepository parametreRepository;
    @Autowired private FinanceParentService financeParentService;
    @Autowired private RapportPdfService rapportPdfService;
    @Autowired private RapportCacheService rapportCacheService;
    @Autowired private SiteVitrineRepository siteVitrineRepository;
    @Autowired private TemoignageSiteRepository temoignageSiteRepository;
    @Autowired private MembreEquipePublicRepository membreEquipePublicRepository;

    /**
     * Outils Claude autorises par role — reprend volontairement les memes frontieres que
     * SecurityConfig (ex: finances reservees a ADMIN/TRESORIER, personnel non visible du
     * SURVEILLANT/INFIRMIER...) pour qu'un role ne puisse jamais obtenir via l'assistant une
     * donnee qu'il ne pourrait pas deja consulter ailleurs dans l'application. Le PARENT a ses
     * propres outils, strictement limites a ses enfants (voir mes_enfants/solde_mon_enfant/
     * absences_mon_enfant). ELEVE n'a pas acces a l'assistant du tout (SecurityConfig).
     */
    private static final Map<String, List<String>> OUTILS_PAR_ROLE = Map.ofEntries(
            Map.entry("ADMIN", List.of("rechercher_eleve", "solde_eleve", "statistiques_etablissement",
                    "absences_recentes", "lister_eleves", "lister_personnel", "resume_financier_etablissement",
                    "generer_rapport_pdf")),
            Map.entry("SUPER_ADMIN", List.of("rechercher_eleve", "solde_eleve", "statistiques_etablissement",
                    "absences_recentes", "lister_eleves", "lister_personnel", "resume_financier_etablissement",
                    "generer_rapport_pdf")),
            Map.entry("ENSEIGNANT", List.of("rechercher_eleve", "statistiques_etablissement", "absences_recentes",
                    "lister_eleves", "lister_personnel", "generer_rapport_pdf")),
            Map.entry("SECRETAIRE", List.of("rechercher_eleve", "statistiques_etablissement", "absences_recentes",
                    "lister_eleves", "lister_personnel", "generer_rapport_pdf")),
            Map.entry("TRESORIER", List.of("rechercher_eleve", "solde_eleve", "statistiques_etablissement",
                    "lister_eleves", "lister_personnel", "resume_financier_etablissement", "generer_rapport_pdf")),
            Map.entry("COORDONNATEUR", List.of("rechercher_eleve", "statistiques_etablissement", "absences_recentes",
                    "lister_eleves", "lister_personnel", "generer_rapport_pdf")),
            Map.entry("SURVEILLANT", List.of("rechercher_eleve", "statistiques_etablissement", "absences_recentes",
                    "generer_rapport_pdf")),
            Map.entry("INFIRMIER", List.of("rechercher_eleve", "statistiques_etablissement", "generer_rapport_pdf")),
            Map.entry("PARENT", List.of("mes_enfants", "solde_mon_enfant", "absences_mon_enfant", "generer_rapport_pdf")),
            // MARKETING pilote le site vitrine public — aucun acces aux donnees d'eleve, de
            // personnel ou de finances, seulement les effectifs globaux (utiles pour rediger
            // du contenu), la generation de rapports, et la personnalisation du site public.
            Map.entry("MARKETING", List.of("statistiques_etablissement", "generer_rapport_pdf",
                    "modifier_apparence_site", "ajouter_temoignage", "ajouter_membre_equipe")));

    public static class Tour {
        public String role; // "user" ou "assistant"
        public String contenu;
    }

    /** Reponse de l'assistant : texte a afficher, et lien de telechargement si un rapport PDF a ete genere. */
    public static class ReponseAssistant {
        public String texte;
        public String lienRapport;

        public ReponseAssistant(String texte, String lienRapport) {
            this.texte = texte;
            this.lienRapport = lienRapport;
        }
    }

    public boolean estConfigure() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Assistant d'accompagnement public sur le formulaire d'inscription d'un etablissement
     * (avant toute authentification). Aucun outil, aucun acces aux donnees : purement
     * conversationnel, pour guider le remplissage du formulaire — impossible donc que cette
     * route publique expose la moindre donnee d'un etablissement existant.
     */
    public ReponseAssistant repondreOnboarding(String question, List<Tour> historique) {
        if (!estConfigure()) {
            return new ReponseAssistant(
                    "L'assistant n'est pas encore configure : la variable d'environnement ANTHROPIC_API_KEY est absente.", null);
        }
        try {
            return appelerAssistantOnboarding(question, historique);
        } catch (com.anthropic.errors.AnthropicServiceException e) {
            return new ReponseAssistant("L'assistant n'a pas pu repondre (erreur du service Claude) : " + e.getMessage(), null);
        } catch (Exception e) {
            return new ReponseAssistant(
                    "L'assistant n'a pas pu repondre : erreur inattendue (" + e.getClass().getSimpleName() + ").", null);
        }
    }

    private ReponseAssistant appelerAssistantOnboarding(String question, List<Tour> historique) {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();

        List<MessageParam> messages = new ArrayList<>();
        if (historique != null) {
            for (Tour t : historique) {
                MessageParam.Role role = "assistant".equals(t.role) ? MessageParam.Role.ASSISTANT : MessageParam.Role.USER;
                messages.add(MessageParam.builder().role(role).content(t.contenu).build());
            }
        }
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(question).build());

        String systeme = "Tu es l'assistant d'inscription d'EduSystem Pro, un logiciel de gestion scolaire. "
                + "Tu aides un visiteur pas encore connecte a remplir le formulaire de creation de son "
                + "etablissement, en francais, de facon concise et bienveillante. Le formulaire comporte 4 etapes : "
                + "(1) Informations generales — nom de l'ecole, categorie, niveaux enseignes, adresse, email, "
                + "telephone ; (2) Parametres academiques — dates de debut/fin d'annee scolaire, systeme de "
                + "notation (numerique /20 ou lettres), seuil d'assiduite minimum, regles d'alerte d'absences et "
                + "de calcul des retards ; (3) Personnalisation — logo, couleur principale, langue du systeme ; "
                + "(4) Compte administrateur — nom complet et email de la personne qui gerera l'etablissement (un "
                + "mot de passe et un code d'acces sont generes automatiquement a la derniere etape). Explique les "
                + "champs, rassure sur le fait que presque tout est modifiable plus tard depuis les Parametres une "
                + "fois connecte, et si on te pose une question generale sur l'application (fonctionnalites, "
                + "tarifs, engagement), reponds avec ce que tu sais du produit sans jamais inventer de prix ou de "
                + "conditions commerciales precises — invite alors a contacter l'equipe. Tu n'as acces a AUCUN "
                + "outil ni AUCUNE donnee d'etablissement existant : ne pretends jamais consulter des donnees "
                + "reelles, et ne demande jamais a l'utilisateur un mot de passe ou une information sensible dans "
                + "cette conversation.";

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system(systeme)
                .messages(messages)
                .build();

        com.anthropic.models.messages.Message reponse = client.messages().create(params);
        StringBuilder texte = new StringBuilder();
        for (ContentBlock bloc : reponse.content()) {
            bloc.text().ifPresent(t -> texte.append(t.text()));
        }
        String texteFinal = texte.length() > 0 ? texte.toString()
                : "Je n'ai pas pu generer de reponse. Reessayez en reformulant votre question.";
        return new ReponseAssistant(texteFinal, null);
    }

    public ReponseAssistant repondre(String question, List<Tour> historique, Utilisateur utilisateurConnecte,
            Long etabId, String nomEtablissement) {
        if (!estConfigure()) {
            return new ReponseAssistant(
                    "L'assistant n'est pas encore configure : la variable d'environnement ANTHROPIC_API_KEY est absente.", null);
        }
        if (etabId == null) {
            return new ReponseAssistant("Impossible de determiner votre etablissement.", null);
        }

        try {
            return appelerAssistant(question, historique, utilisateurConnecte, etabId, nomEtablissement);
        } catch (com.anthropic.errors.AnthropicServiceException e) {
            return new ReponseAssistant("L'assistant n'a pas pu repondre (erreur du service Claude) : " + e.getMessage(), null);
        } catch (Exception e) {
            return new ReponseAssistant(
                    "L'assistant n'a pas pu repondre : erreur inattendue (" + e.getClass().getSimpleName() + ").", null);
        }
    }

    private ReponseAssistant appelerAssistant(String question, List<Tour> historique, Utilisateur utilisateurConnecte,
            Long etabId, String nomEtablissement) {
        AnthropicClient client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();

        List<MessageParam> messages = new ArrayList<>();
        if (historique != null) {
            for (Tour t : historique) {
                MessageParam.Role role = "assistant".equals(t.role) ? MessageParam.Role.ASSISTANT : MessageParam.Role.USER;
                messages.add(MessageParam.builder().role(role).content(t.contenu).build());
            }
        }
        messages.add(MessageParam.builder().role(MessageParam.Role.USER).content(question).build());

        boolean estParent = "PARENT".equals(utilisateurConnecte.getRole());
        String systeme = "Tu es l'assistant integre a EduSystem Pro, un logiciel de gestion scolaire, "
                + "pour l'etablissement « " + nomEtablissement + " ». "
                + "Tu reponds a " + utilisateurConnecte.getPrenom() + " " + utilisateurConnecte.getNom()
                + " (role " + utilisateurConnecte.getRole() + "), en francais, de facon concise et precise. "
                + (estParent
                        ? "Ce compte est un compte PARENT : tu ne peux consulter que les informations de SES PROPRES "
                                + "enfants via les outils mes_enfants/solde_mon_enfant/absences_mon_enfant — jamais "
                                + "les donnees d'autres eleves ou de l'etablissement dans son ensemble. "
                        : "")
                + "IMPORTANT : les outils fournis dans cette conversation sont deja limites a ce que ce role a le "
                + "droit de consulter dans l'application — n'essaie jamais de deviner ou d'inventer une information "
                + "hors de ces outils ; si aucun outil ne permet de repondre, dis-le clairement et explique que "
                + "cette information n'est pas accessible depuis ce role, plutot que d'inventer une reponse. "
                + "Ne reponds jamais de memoire ni en inventant des chiffres. "
                + "Quand on te demande un rapport, un document imprimable, un effectif, un bilan financier ou un "
                + "proces-verbal (PV) mensuel : recupere d'abord les donnees reelles necessaires avec les outils de "
                + "recherche disponibles, PUIS appelle generer_rapport_pdf en composant les sections uniquement a "
                + "partir de ces donnees reelles — jamais de valeurs inventees. Pour un PV mensuel, reprends fidelement "
                + "l'ordre du jour, les presents et les decisions donnes par l'utilisateur dans la conversation, sans "
                + "en inventer d'autres. Une fois generer_rapport_pdf appele avec succes, indique simplement a "
                + "l'utilisateur que le rapport est pret et que le lien de telechargement s'affiche ci-dessous — ne "
                + "redonne jamais toi-meme d'URL ou de lien.";

        List<String> nomsOutilsAutorises = OUTILS_PAR_ROLE.getOrDefault(utilisateurConnecte.getRole(), List.of());
        Map<String, Tool> tousLesOutils = tousLesOutils();

        AtomicReference<String> lienRapportGenere = new AtomicReference<>();
        int maxTours = 6;
        for (int tour = 0; tour < maxTours; tour++) {
            MessageCreateParams.Builder constructeur = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(2048L)
                    .system(systeme)
                    .messages(messages);
            for (String nomOutil : nomsOutilsAutorises) {
                Tool outil = tousLesOutils.get(nomOutil);
                if (outil != null) constructeur.addTool(outil);
            }
            MessageCreateParams params = constructeur.build();

            com.anthropic.models.messages.Message reponse = client.messages().create(params);

            List<ToolUseBlock> appelsOutils = new ArrayList<>();
            StringBuilder texte = new StringBuilder();
            List<ContentBlockParam> contenuAssistant = new ArrayList<>();
            for (ContentBlock bloc : reponse.content()) {
                bloc.text().ifPresent(t -> {
                    texte.append(t.text());
                    contenuAssistant.add(ContentBlockParam.ofText(t.toParam()));
                });
                bloc.toolUse().ifPresent(tu -> {
                    appelsOutils.add(tu);
                    contenuAssistant.add(ContentBlockParam.ofToolUse(tu.toParam()));
                });
            }

            if (appelsOutils.isEmpty()) {
                String texteFinal = texte.length() > 0 ? texte.toString()
                        : "Je n'ai pas pu generer de reponse. Reessayez en reformulant votre question.";
                return new ReponseAssistant(texteFinal, lienRapportGenere.get());
            }

            messages.add(MessageParam.builder().role(MessageParam.Role.ASSISTANT).contentOfBlockParams(contenuAssistant).build());

            List<ContentBlockParam> resultats = new ArrayList<>();
            for (ToolUseBlock tu : appelsOutils) {
                String resultat = executerOutil(tu.name(), lireEntrees(tu), etabId, nomEtablissement,
                        utilisateurConnecte, lienRapportGenere);
                resultats.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder().toolUseId(tu.id()).content(resultat).build()));
            }
            messages.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(resultats).build());
        }

        return new ReponseAssistant(
                "Je n'ai pas pu conclure apres plusieurs recherches. Precisez votre question.", lienRapportGenere.get());
    }

    // ──────────────────────────────────────────────────────────────
    // Definitions des outils
    // ──────────────────────────────────────────────────────────────

    private Tool outilRechercheEleve() {
        return Tool.builder()
                .name("rechercher_eleve")
                .description("Recherche un ou plusieurs eleves de l'etablissement par nom, prenom ou matricule. "
                        + "Retourne leur classe et leur statut d'inscription.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("recherche", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "Nom, prenom ou matricule (partiel accepte) de l'eleve recherche")))
                                .build())
                        .required(List.of("recherche"))
                        .build())
                .build();
    }

    private Tool outilSoldeEleve() {
        return Tool.builder()
                .name("solde_eleve")
                .description("Donne la situation financiere (solde a regler, retards de paiement) d'un eleve, "
                        + "identifie par son nom ou son matricule.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("recherche", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "Nom, prenom ou matricule de l'eleve")))
                                .build())
                        .required(List.of("recherche"))
                        .build())
                .build();
    }

    private Tool outilStatistiquesEtablissement() {
        return Tool.builder()
                .name("statistiques_etablissement")
                .description("Retourne les effectifs globaux de l'etablissement : nombre total d'eleves, "
                        + "de personnels et de classes.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder().build())
                        .build())
                .build();
    }

    private Tool outilAbsencesRecentes() {
        return Tool.builder()
                .name("absences_recentes")
                .description("Compte les absences enregistrees dans l'etablissement sur les N derniers jours.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("jours", JsonValue.from(Map.of(
                                        "type", "integer",
                                        "description", "Nombre de jours a regarder en arriere (7 par defaut)")))
                                .build())
                        .build())
                .build();
    }

    private Tool outilListerEleves() {
        return Tool.builder()
                .name("lister_eleves")
                .description("Liste les eleves de l'etablissement (matricule, nom, prenom, genre, classe, statut), "
                        + "avec filtre optionnel par classe. Utile pour construire un effectif imprimable.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("classe", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "Nom (partiel accepte) de la classe pour filtrer, vide pour tous les eleves")))
                                .build())
                        .build())
                .build();
    }

    private Tool outilListerPersonnel() {
        return Tool.builder()
                .name("lister_personnel")
                .description("Liste le personnel de l'etablissement (nom, prenom, fonction, statut, telephone, email), "
                        + "avec filtre optionnel par fonction (ENSEIGNANT, DIRECTEUR, SECRETAIRE, SURVEILLANT, COMPTABLE, AUTRE).")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("fonction", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "Fonction pour filtrer (optionnel)")))
                                .build())
                        .build())
                .build();
    }

    private Tool outilResumeFinancierEtablissement() {
        return Tool.builder()
                .name("resume_financier_etablissement")
                .description("Donne un resume financier global de l'etablissement : total encaisse, nombre de "
                        + "paiements, solde total restant a regler et nombre d'eleves en retard de paiement.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder().build())
                        .build())
                .build();
    }

    private Tool outilGenererRapportPdf() {
        Map<String, Object> itemSection = Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of(
                                "type", "string",
                                "enum", List.of("tableau", "texte"),
                                "description", "\"tableau\" pour des donnees en colonnes/lignes, \"texte\" pour un paragraphe libre"),
                        "titre", Map.of("type", "string", "description", "Titre de la section (optionnel)"),
                        "colonnes", Map.of("type", "array", "items", Map.of("type", "string"),
                                "description", "En-tetes de colonnes, uniquement pour type=tableau"),
                        "lignes", Map.of("type", "array", "items", Map.of(
                                        "type", "array", "items", Map.of("type", "string")),
                                "description", "Lignes de donnees (chaque ligne = liste de valeurs alignees avec colonnes), "
                                        + "uniquement pour type=tableau"),
                        "texte", Map.of("type", "string", "description", "Contenu du paragraphe, uniquement pour type=texte")),
                "required", List.of("type"));
        Map<String, Object> schemaSections = Map.of(
                "type", "array",
                "description", "Liste ordonnee des sections du document, dans l'ordre d'affichage souhaite.",
                "items", itemSection);

        return Tool.builder()
                .name("generer_rapport_pdf")
                .description("Genere une feuille de rapport PDF prete a imprimer, avec l'en-tete officiel de "
                        + "l'etablissement. La mise en page est libre : compose les sections dans l'ordre voulu, "
                        + "avec UNIQUEMENT des donnees obtenues via les autres outils (jamais de chiffres inventes). "
                        + "Utilise ceci pour tout rapport, effectif imprimable, situation financiere, releve "
                        + "d'absences, liste du personnel, ou proces-verbal mensuel.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("titre", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "Titre principal du document (ex: RAPPORT D'EFFECTIFS, PROCES-VERBAL MENSUEL)")))
                                .putAdditionalProperty("sousTitre", JsonValue.from(Map.of(
                                        "type", "string", "description", "Sous-titre (optionnel)")))
                                .putAdditionalProperty("periode", JsonValue.from(Map.of(
                                        "type", "string", "description", "Periode concernee, ex: Aout 2026, Trimestre 1 (optionnel)")))
                                .putAdditionalProperty("sections", JsonValue.from(schemaSections))
                                .build())
                        .required(List.of("titre", "sections"))
                        .build())
                .build();
    }

    private Tool outilMesEnfants() {
        return Tool.builder()
                .name("mes_enfants")
                .description("Liste les enfants (eleves) rattaches a votre compte parent, avec leur classe et statut.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder().build())
                        .build())
                .build();
    }

    private Tool outilSoldeMonEnfant() {
        return Tool.builder()
                .name("solde_mon_enfant")
                .description("Donne la situation financiere (solde a regler, echeances en retard) de vos enfants "
                        + "inscrits dans l'etablissement.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("enfant", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "Nom de l'enfant si vous en avez plusieurs (optionnel)")))
                                .build())
                        .build())
                .build();
    }

    private Tool outilAbsencesMonEnfant() {
        return Tool.builder()
                .name("absences_mon_enfant")
                .description("Liste les absences recentes de vos enfants inscrits dans l'etablissement.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("jours", JsonValue.from(Map.of(
                                        "type", "integer",
                                        "description", "Nombre de jours a regarder en arriere (30 par defaut)")))
                                .build())
                        .build())
                .build();
    }

    private Tool outilModifierApparenceSite() {
        return Tool.builder()
                .name("modifier_apparence_site")
                .description("Modifie l'apparence du site vitrine public : URL d'une video de couverture (YouTube "
                        + "ou Vimeo), couleur d'accent secondaire (hex, ex: #c9a227), ou style de typographie. "
                        + "IMPORTANT : ne peut PAS changer l'image de couverture (photo) — un upload de fichier "
                        + "est necessaire depuis le tableau de bord Marketing pour cela ; dis-le clairement a "
                        + "l'utilisateur s'il demande une photo. Ne modifie que les champs fournis, les autres "
                        + "restent inchanges.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("heroVideoUrl", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "URL YouTube ou Vimeo a utiliser comme fond de la section d'accueil (optionnel)")))
                                .putAdditionalProperty("couleurSecondaire", JsonValue.from(Map.of(
                                        "type", "string",
                                        "description", "Couleur d'accent secondaire au format hexadecimal, ex: #c9a227 (optionnel)")))
                                .putAdditionalProperty("police", JsonValue.from(Map.of(
                                        "type", "string",
                                        "enum", List.of("CLASSIQUE", "MODERNE", "ELEGANTE"),
                                        "description", "Style de typographie du site public (optionnel)")))
                                .build())
                        .build())
                .build();
    }

    private Tool outilAjouterTemoignage() {
        return Tool.builder()
                .name("ajouter_temoignage")
                .description("Ajoute un temoignage (parent, ancien eleve...) affiche sur le site vitrine public. "
                        + "Utilise UNIQUEMENT le texte fourni explicitement par l'utilisateur dans la conversation "
                        + "— n'invente jamais de contenu ni de nom d'auteur.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("auteur", JsonValue.from(Map.of(
                                        "type", "string", "description", "Nom de la personne qui temoigne")))
                                .putAdditionalProperty("role", JsonValue.from(Map.of(
                                        "type", "string", "description", "Ex: Parent d'eleve, Ancien eleve (optionnel)")))
                                .putAdditionalProperty("contenu", JsonValue.from(Map.of(
                                        "type", "string", "description", "Texte du temoignage")))
                                .build())
                        .required(List.of("auteur", "contenu"))
                        .build())
                .build();
    }

    private Tool outilAjouterMembreEquipe() {
        return Tool.builder()
                .name("ajouter_membre_equipe")
                .description("Ajoute un membre de l'equipe mis en avant sur le site vitrine public (nom, fonction, "
                        + "courte bio). IMPORTANT : n'ajoute PAS de photo — un upload de fichier depuis le tableau "
                        + "de bord Marketing est necessaire pour cela, dis-le a l'utilisateur si besoin.")
                .inputSchema(Tool.InputSchema.builder()
                        .properties(Tool.InputSchema.Properties.builder()
                                .putAdditionalProperty("nom", JsonValue.from(Map.of(
                                        "type", "string", "description", "Nom du membre de l'equipe")))
                                .putAdditionalProperty("fonction", JsonValue.from(Map.of(
                                        "type", "string", "description", "Fonction affichee publiquement (optionnel)")))
                                .putAdditionalProperty("bio", JsonValue.from(Map.of(
                                        "type", "string", "description", "Courte presentation (optionnel)")))
                                .build())
                        .required(List.of("nom"))
                        .build())
                .build();
    }

    /** Registre de tous les outils definis ; le sous-ensemble expose a Claude est filtre par OUTILS_PAR_ROLE. */
    private Map<String, Tool> tousLesOutils() {
        Map<String, Tool> m = new LinkedHashMap<>();
        m.put("rechercher_eleve", outilRechercheEleve());
        m.put("solde_eleve", outilSoldeEleve());
        m.put("statistiques_etablissement", outilStatistiquesEtablissement());
        m.put("absences_recentes", outilAbsencesRecentes());
        m.put("lister_eleves", outilListerEleves());
        m.put("lister_personnel", outilListerPersonnel());
        m.put("resume_financier_etablissement", outilResumeFinancierEtablissement());
        m.put("generer_rapport_pdf", outilGenererRapportPdf());
        m.put("mes_enfants", outilMesEnfants());
        m.put("solde_mon_enfant", outilSoldeMonEnfant());
        m.put("absences_mon_enfant", outilAbsencesMonEnfant());
        m.put("modifier_apparence_site", outilModifierApparenceSite());
        m.put("ajouter_temoignage", outilAjouterTemoignage());
        m.put("ajouter_membre_equipe", outilAjouterMembreEquipe());
        return m;
    }

    // ──────────────────────────────────────────────────────────────
    // Execution des outils — etabId vient TOUJOURS du serveur, jamais du modele.
    // Pour le PARENT, les enfants sont resolus depuis utilisateurConnecte.getEmail(),
    // jamais depuis une entree fournie par le modele.
    // ──────────────────────────────────────────────────────────────

    private String executerOutil(String nom, Map<String, Object> input, Long etabId, String nomEtablissement,
            Utilisateur utilisateurConnecte, AtomicReference<String> lienRapportGenere) {
        try {
            return switch (nom) {
                case "rechercher_eleve" -> rechercherEleve(texteEntree(input, "recherche"), etabId);
                case "solde_eleve" -> soldeEleve(texteEntree(input, "recherche"), etabId);
                case "statistiques_etablissement" -> statistiquesEtablissement(etabId);
                case "absences_recentes" -> absencesRecentes(entierEntree(input, "jours", 7), etabId);
                case "lister_eleves" -> listerEleves(texteEntree(input, "classe"), etabId);
                case "lister_personnel" -> listerPersonnel(texteEntree(input, "fonction"), etabId);
                case "resume_financier_etablissement" -> resumeFinancierEtablissement(etabId);
                case "generer_rapport_pdf" -> genererRapportPdf(input, etabId, nomEtablissement, lienRapportGenere);
                case "mes_enfants" -> mesEnfants(utilisateurConnecte);
                case "solde_mon_enfant" -> soldeMonEnfant(texteEntree(input, "enfant"), utilisateurConnecte, etabId);
                case "absences_mon_enfant" -> absencesMonEnfant(entierEntree(input, "jours", 30), utilisateurConnecte);
                case "modifier_apparence_site" -> modifierApparenceSite(input, etabId);
                case "ajouter_temoignage" -> ajouterTemoignage(input, etabId);
                case "ajouter_membre_equipe" -> ajouterMembreEquipe(input, etabId);
                default -> "Outil inconnu.";
            };
        } catch (Exception e) {
            return "Erreur lors de l'execution de l'outil : " + e.getMessage();
        }
    }

    /** Convertit l'entree JSON brute d'un appel d'outil en Map exploitable cote Java. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> lireEntrees(ToolUseBlock tu) {
        Object valeur = tu._input().convert(Map.class);
        return valeur instanceof Map ? (Map<String, Object>) valeur : Map.of();
    }

    private String texteEntree(Map<String, Object> input, String cle) {
        Object v = input.get(cle);
        return v == null ? "" : String.valueOf(v);
    }

    private int entierEntree(Map<String, Object> input, String cle, int defaut) {
        Object v = input.get(cle);
        if (v == null) return defaut;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    private String rechercherEleve(String recherche, Long etabId) {
        if (recherche == null || recherche.isBlank()) return "Aucun critere de recherche fourni.";
        List<Eleve> resultats = eleveRepository.searchByEtablissement(recherche, etabId);
        if (resultats.isEmpty()) return "Aucun eleve trouve pour « " + recherche + " » dans cet etablissement.";
        StringBuilder sb = new StringBuilder();
        for (Eleve e : resultats) {
            sb.append("- ").append(e.getNom()).append(" ").append(e.getPrenom())
                    .append(" (matricule ").append(e.getMatricule()).append(") — classe : ")
                    .append(e.getClasse() != null ? e.getClasse().getNom() : "non affecte")
                    .append(" — statut : ").append(e.getStatutInscription()).append("\n");
        }
        return sb.toString();
    }

    private String soldeEleve(String recherche, Long etabId) {
        if (recherche == null || recherche.isBlank()) return "Aucun critere de recherche fourni.";
        List<Eleve> resultats = eleveRepository.searchByEtablissement(recherche, etabId);
        if (resultats.isEmpty()) return "Aucun eleve trouve pour « " + recherche + " » dans cet etablissement.";
        if (resultats.size() > 1) {
            StringBuilder sb = new StringBuilder("Plusieurs eleves correspondent, precisez lequel :\n");
            for (Eleve e : resultats) sb.append("- ").append(e.getNom()).append(" ").append(e.getPrenom()).append("\n");
            return sb.toString();
        }
        Eleve e = resultats.get(0);
        FinanceParentService.ResumeSolde resume = financeParentService.calculerResume(List.of(e), etabId);
        return e.getNom() + " " + e.getPrenom() + " : solde a regler = "
                + resume.soldeTotalARegler + " FCFA, echeances en retard = " + resume.nbEnRetard + ".";
    }

    private String statistiquesEtablissement(Long etabId) {
        long eleves = eleveRepository.countByEtablissementId(etabId);
        long personnels = personnelRepository.countByEtablissementId(etabId);
        long classes = classeRepository.findByEtablissementId(etabId).size();
        return "Effectifs : " + eleves + " eleve(s), " + personnels + " personnel(s), " + classes + " classe(s).";
    }

    private String absencesRecentes(int jours, Long etabId) {
        LocalDate seuil = LocalDate.now().minusDays(Math.max(1, jours));
        List<Absence> absences = absenceRepository.findByEtablissementId(etabId);
        long recentes = absences.stream().filter(a -> a.getDate() != null && !a.getDate().isBefore(seuil)).count();
        long nonJustifiees = absences.stream()
                .filter(a -> a.getDate() != null && !a.getDate().isBefore(seuil) && !a.isEstJustifiee())
                .count();
        return recentes + " absence(s) enregistree(s) sur les " + jours + " derniers jours, dont "
                + nonJustifiees + " non justifiee(s).";
    }

    private String listerEleves(String classeFiltre, Long etabId) {
        List<Eleve> eleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        if (classeFiltre != null && !classeFiltre.isBlank()) {
            String f = classeFiltre.toLowerCase();
            eleves = eleves.stream()
                    .filter(e -> e.getClasse() != null && e.getClasse().getNom() != null
                            && e.getClasse().getNom().toLowerCase().contains(f))
                    .toList();
        }
        if (eleves.isEmpty()) {
            return classeFiltre == null || classeFiltre.isBlank()
                    ? "Aucun eleve trouve dans cet etablissement."
                    : "Aucun eleve trouve pour la classe « " + classeFiltre + " ».";
        }
        int limite = Math.min(eleves.size(), 300);
        StringBuilder sb = new StringBuilder(eleves.size() + " eleve(s) :\n");
        for (int i = 0; i < limite; i++) {
            Eleve e = eleves.get(i);
            sb.append("- ").append(e.getMatricule()).append(" | ").append(e.getNom()).append(" ").append(e.getPrenom())
                    .append(" | ").append(e.getGenre() != null ? e.getGenre() : "-")
                    .append(" | classe : ").append(e.getClasse() != null ? e.getClasse().getNom() : "non affecte")
                    .append(" | statut : ").append(e.getStatutInscription()).append("\n");
        }
        if (eleves.size() > limite) {
            sb.append("... (").append(eleves.size() - limite).append(" de plus, non affiches ici)\n");
        }
        return sb.toString();
    }

    private String listerPersonnel(String fonctionFiltre, Long etabId) {
        List<Personnel> personnels = (fonctionFiltre != null && !fonctionFiltre.isBlank())
                ? personnelRepository.findByFonctionAndEtablissementIdOrderByNomAsc(fonctionFiltre.toUpperCase(), etabId)
                : personnelRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        if (personnels.isEmpty()) return "Aucun personnel trouve.";
        StringBuilder sb = new StringBuilder(personnels.size() + " personnel(s) :\n");
        for (Personnel p : personnels) {
            sb.append("- ").append(p.getNom()).append(" ").append(p.getPrenom())
                    .append(" | fonction : ").append(p.getFonction())
                    .append(" | statut : ").append(p.getStatut())
                    .append(" | telephone : ").append(p.getTelephone() != null ? p.getTelephone() : "-")
                    .append(" | email : ").append(p.getEmail() != null ? p.getEmail() : "-").append("\n");
        }
        return sb.toString();
    }

    private String resumeFinancierEtablissement(Long etabId) {
        List<Paiement> paiements = paiementRepository.findByEtablissementId(etabId);
        double totalEncaisse = paiements.stream()
                .mapToDouble(p -> p.getMontantVerse() != null ? p.getMontantVerse() : 0).sum();
        List<Eleve> eleves = eleveRepository.findByEtablissementIdOrderByNomAscPrenomAsc(etabId);
        FinanceParentService.ResumeSolde resume = financeParentService.calculerResume(eleves, etabId);
        return "Total encaisse : " + totalEncaisse + " FCFA sur " + paiements.size() + " paiement(s). "
                + "Solde total restant a regler pour l'etablissement : " + resume.soldeTotalARegler + " FCFA. "
                + "Nombre d'eleves avec au moins une echeance en retard : " + resume.nbEnRetard + ".";
    }

    @SuppressWarnings("unchecked")
    private String genererRapportPdf(Map<String, Object> input, Long etabId, String nomEtablissement,
            AtomicReference<String> lienRapportGenere) {
        String titre = texteEntree(input, "titre");
        if (titre.isBlank()) return "Le titre du rapport est requis.";
        String sousTitre = texteEntree(input, "sousTitre");
        String periode = texteEntree(input, "periode");

        Object sectionsBrutes = input.get("sections");
        if (!(sectionsBrutes instanceof List<?> liste) || liste.isEmpty()) {
            return "Aucune section fournie : impossible de generer le rapport.";
        }
        List<Map<String, Object>> sections = new ArrayList<>();
        for (Object o : liste) {
            if (o instanceof Map<?, ?> m) sections.add((Map<String, Object>) m);
        }
        if (sections.isEmpty()) return "Aucune section valide fournie : impossible de generer le rapport.";

        byte[] pdf = rapportPdfService.genererPdf(titre, sousTitre, periode, sections,
                construireEnTeteEtablissement(etabId, nomEtablissement));
        String id = rapportCacheService.stocker(pdf, etabId);
        lienRapportGenere.set("/assistant/rapport/" + id + ".pdf");
        return "Rapport PDF genere avec succes (" + sections.size() + " section(s)). Le lien de telechargement "
                + "sera affiche automatiquement a l'utilisateur : ne l'invente pas et ne le repete pas toi-meme.";
    }

    private Map<String, Object> construireEnTeteEtablissement(Long etabId, String nomEtablissement) {
        Map<String, Object> enTete = new LinkedHashMap<>();
        enTete.put("nomEtab", nomEtablissement);
        enTete.put("logoPath", parametreRepository.findByCleAndEtablissementId("LOGO_ETAB", etabId)
                .map(p -> p.getValeur()).filter(v -> v != null && !v.isBlank()).orElse(null));
        return enTete;
    }

    // ──────────────────────────────────────────────────────────────
    // Outils PARENT — les enfants sont TOUJOURS resolus depuis l'email du compte connecte
    // (utilisateurConnecte.getEmail()), jamais depuis un nom/ID fourni par le modele : un parent
    // ne peut donc jamais, via une instruction glissee dans la conversation, faire remonter les
    // donnees d'un enfant qui n'est pas le sien.
    // ──────────────────────────────────────────────────────────────

    private String mesEnfants(Utilisateur utilisateurConnecte) {
        List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(utilisateurConnecte.getEmail());
        if (enfants.isEmpty()) return "Aucun enfant rattache a votre compte.";
        StringBuilder sb = new StringBuilder();
        for (Eleve e : enfants) {
            sb.append("- ").append(e.getNom()).append(" ").append(e.getPrenom())
                    .append(" | matricule : ").append(e.getMatricule())
                    .append(" | classe : ").append(e.getClasse() != null ? e.getClasse().getNom() : "non affecte")
                    .append(" | statut : ").append(e.getStatutInscription()).append("\n");
        }
        return sb.toString();
    }

    private String soldeMonEnfant(String filtreNom, Utilisateur utilisateurConnecte, Long etabId) {
        List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(utilisateurConnecte.getEmail());
        if (enfants.isEmpty()) return "Aucun enfant rattache a votre compte.";
        if (filtreNom != null && !filtreNom.isBlank()) {
            String f = filtreNom.toLowerCase();
            List<Eleve> filtres = enfants.stream()
                    .filter(e -> e.getNom().toLowerCase().contains(f) || e.getPrenom().toLowerCase().contains(f))
                    .toList();
            if (!filtres.isEmpty()) enfants = filtres;
        }
        Long etabEffectif = etabId != null ? etabId : enfants.get(0).getEtablissementId();
        FinanceParentService.ResumeSolde resume = financeParentService.calculerResume(enfants, etabEffectif);
        StringBuilder noms = new StringBuilder();
        for (Eleve e : enfants) noms.append(e.getNom()).append(" ").append(e.getPrenom()).append(", ");
        return "Situation de " + noms + ": solde total a regler = " + resume.soldeTotalARegler
                + " FCFA, echeances en retard = " + resume.nbEnRetard + ".";
    }

    private String absencesMonEnfant(int jours, Utilisateur utilisateurConnecte) {
        List<Eleve> enfants = eleveRepository.findAllByParentEmailAnyOrderByNomAsc(utilisateurConnecte.getEmail());
        if (enfants.isEmpty()) return "Aucun enfant rattache a votre compte.";
        LocalDate seuil = LocalDate.now().minusDays(Math.max(1, jours));
        StringBuilder sb = new StringBuilder();
        for (Eleve e : enfants) {
            List<Absence> absences = absenceRepository.findByEleveIdOrderByDateDesc(e.getId());
            long recentes = absences.stream().filter(a -> a.getDate() != null && !a.getDate().isBefore(seuil)).count();
            long nonJustifiees = absences.stream()
                    .filter(a -> a.getDate() != null && !a.getDate().isBefore(seuil) && !a.isEstJustifiee())
                    .count();
            sb.append(e.getNom()).append(" ").append(e.getPrenom()).append(" : ").append(recentes)
                    .append(" absence(s) sur les ").append(jours).append(" derniers jours, dont ")
                    .append(nonJustifiees).append(" non justifiee(s).\n");
        }
        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────────
    // Outils MARKETING — personnalisation du site vitrine public. Les images restent hors de
    // portee de l'assistant (aucun mecanisme d'upload dans une conversation) : seuls les champs
    // exprimables en texte (URL video, couleur hex, choix de police) sont modifiables ici.
    // ──────────────────────────────────────────────────────────────

    private SiteVitrine obtenirOuCreerSiteVitrine(Long etabId) {
        return siteVitrineRepository.findByEtablissementId(etabId).orElseGet(() -> {
            SiteVitrine s = new SiteVitrine();
            s.setEtablissementId(etabId);
            s.setActif(false);
            return siteVitrineRepository.save(s);
        });
    }

    private String modifierApparenceSite(Map<String, Object> input, Long etabId) {
        SiteVitrine site = obtenirOuCreerSiteVitrine(etabId);
        List<String> changements = new ArrayList<>();

        String heroVideoUrl = texteEntree(input, "heroVideoUrl");
        if (!heroVideoUrl.isBlank()) {
            site.setHeroVideoUrl(heroVideoUrl);
            changements.add("vidéo de couverture");
        }
        String couleurSecondaire = texteEntree(input, "couleurSecondaire");
        if (!couleurSecondaire.isBlank()) {
            site.setCouleurSecondaire(couleurSecondaire);
            changements.add("couleur secondaire");
        }
        String police = texteEntree(input, "police");
        if (!police.isBlank() && Set.of("CLASSIQUE", "MODERNE", "ELEGANTE").contains(police)) {
            site.setPolice(police);
            changements.add("typographie");
        }

        if (changements.isEmpty()) {
            return "Aucun changement valide fourni. Précise une URL de vidéo, une couleur (hex), ou une police "
                    + "(CLASSIQUE, MODERNE ou ELEGANTE).";
        }
        siteVitrineRepository.save(site);
        return "Apparence du site mise à jour (" + String.join(", ", changements) + "). La photo de couverture, "
                + "elle, doit être ajoutée depuis le tableau de bord Marketing (upload de fichier).";
    }

    private String ajouterTemoignage(Map<String, Object> input, Long etabId) {
        String auteur = texteEntree(input, "auteur");
        String contenu = texteEntree(input, "contenu");
        if (auteur.isBlank() || contenu.isBlank()) return "L'auteur et le contenu du témoignage sont requis.";
        TemoignageSite t = new TemoignageSite();
        t.setEtablissementId(etabId);
        t.setAuteur(auteur);
        t.setRole(texteEntree(input, "role"));
        t.setContenu(contenu);
        t.setDateAjout(java.time.LocalDateTime.now());
        temoignageSiteRepository.save(t);
        return "Témoignage de " + auteur + " ajouté au site public.";
    }

    private String ajouterMembreEquipe(Map<String, Object> input, Long etabId) {
        String nom = texteEntree(input, "nom");
        if (nom.isBlank()) return "Le nom du membre est requis.";
        MembreEquipePublic m = new MembreEquipePublic();
        m.setEtablissementId(etabId);
        m.setNom(nom);
        m.setFonction(texteEntree(input, "fonction"));
        m.setBio(texteEntree(input, "bio"));
        m.setOrdre(0);
        membreEquipePublicRepository.save(m);
        return "Membre " + nom + " ajouté à l'équipe publique du site (sans photo — à ajouter depuis le tableau de bord Marketing).";
    }
}
