package holyflame.administration.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stockage ephemere (en memoire) des PDF de rapport generes par l'assistant, le temps que
 * l'utilisateur clique sur le lien de telechargement affiche dans le chat. Chaque entree est
 * associee a l'etablissement qui l'a generee : le telechargement verifie cette correspondance
 * avant de servir le fichier, pour qu'un identifiant devine ne puisse pas exposer le rapport
 * d'un autre etablissement.
 */
@Service
public class RapportCacheService {

    private static final long DUREE_VIE_MS = 30 * 60 * 1000; // 30 minutes

    private static class Entree {
        byte[] pdf;
        Long etabId;
        long expireAt;
    }

    private final Map<String, Entree> cache = new ConcurrentHashMap<>();

    public String stocker(byte[] pdf, Long etabId) {
        purgerExpires();
        String id = UUID.randomUUID().toString();
        Entree e = new Entree();
        e.pdf = pdf;
        e.etabId = etabId;
        e.expireAt = System.currentTimeMillis() + DUREE_VIE_MS;
        cache.put(id, e);
        return id;
    }

    public byte[] recuperer(String id, Long etabId) {
        Entree e = cache.get(id);
        if (e == null || etabId == null || !etabId.equals(e.etabId) || System.currentTimeMillis() > e.expireAt) {
            return null;
        }
        return e.pdf;
    }

    private void purgerExpires() {
        long maintenant = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().expireAt < maintenant);
    }
}
