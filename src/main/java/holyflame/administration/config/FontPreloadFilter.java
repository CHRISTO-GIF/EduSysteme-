package holyflame.administration.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Precharge la police d'icones Material Symbols (1,1 Mo, aucune police de repli declaree
 * dans fonts.css) des le tout debut du chargement de chaque page, plutot que d'attendre que
 * le CSS soit parse puis qu'une icone soit rencontree a l'affichage. Sans ce prechargement,
 * la premiere page vue dans une session peut afficher brievement le nom brut des icones
 * ("search", "dashboard"...) le temps que la police arrive.
 */
public class FontPreloadFilter extends OncePerRequestFilter {

    private static final String FONT_PATH =
        "/fonts/material-symbols/kJEPBvYX7BgnkSrUwT8OhrdQw4oELdPIeeII9v6oDMzBwG-RpA6RzaxHMPdY40KH8nGzv3fzfVJO1Q.woff2";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean estRessourceStatique = uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/fonts/")
            || uri.startsWith("/images/") || uri.startsWith("/uploads/") || uri.startsWith("/webjars/");
        if (!estRessourceStatique) {
            response.addHeader("Link", "<" + FONT_PATH + ">; rel=preload; as=font; type=font/woff2; crossorigin");
        }
        filterChain.doFilter(request, response);
    }
}
