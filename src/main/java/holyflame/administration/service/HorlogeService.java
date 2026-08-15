package holyflame.administration.service;

import holyflame.administration.model.Etablissement;
import holyflame.administration.repository.EtablissementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Horloge "metier" : renvoie la date/heure actuelle dans le fuseau horaire choisi par
 * l'etablissement (Parametres > Fuseau Horaire), independamment du fuseau du serveur qui
 * heberge l'application. Indispensable pour que les dates d'archivage (notes, paiements,
 * messages, journal d'audit, annee scolaire...) restent exactes quel que soit l'endroit
 * ou l'app tourne — utiliser cette horloge plutot que LocalDate.now()/LocalDateTime.now()
 * pour toute date qui sera enregistree ou affichee comme "aujourd'hui" pour un etablissement.
 * Sans objet pour les delais de securite (expiration de jeton, verrouillage de compte) qui
 * doivent rester bases sur le temps ecoule reel, pas sur un fuseau local.
 */
@Service
public class HorlogeService {

    private static final Map<String, ZoneId> ZONES_CONNUES = Map.of(
            "(GMT+00:00) Abidjan", ZoneId.of("Africa/Abidjan"),
            "(GMT+01:00) N'Djamena", ZoneId.of("Africa/Ndjamena"),
            "(GMT+03:00) Nairobi", ZoneId.of("Africa/Nairobi"));
    private static final ZoneId ZONE_PAR_DEFAUT = ZoneId.of("Africa/Abidjan");

    @Autowired
    private EtablissementService etablissementService;
    @Autowired
    private EtablissementRepository etablissementRepository;

    private ZoneId resoudreZone(String fuseauHoraire) {
        if (fuseauHoraire == null)
            return ZONE_PAR_DEFAUT;
        return ZONES_CONNUES.getOrDefault(fuseauHoraire, ZONE_PAR_DEFAUT);
    }

    /** Fuseau horaire de l'etablissement de l'utilisateur actuellement connecte. */
    public ZoneId zoneCourante() {
        Etablissement etab = etablissementService.getCurrentEtablissement();
        return resoudreZone(etab != null ? etab.getFuseauHoraire() : null);
    }

    /** Fuseau horaire d'un etablissement donne (pour les traitements sans utilisateur connecte). */
    public ZoneId zone(Long etablissementId) {
        if (etablissementId == null)
            return ZONE_PAR_DEFAUT;
        return etablissementRepository.findById(etablissementId)
                .map(e -> resoudreZone(e.getFuseauHoraire()))
                .orElse(ZONE_PAR_DEFAUT);
    }

    public LocalDate aujourdHui() {
        return LocalDate.now(zoneCourante());
    }

    public LocalDateTime maintenant() {
        return LocalDateTime.now(zoneCourante());
    }

    public LocalDate aujourdHui(Long etablissementId) {
        return LocalDate.now(zone(etablissementId));
    }

    public LocalDateTime maintenant(Long etablissementId) {
        return LocalDateTime.now(zone(etablissementId));
    }
}
