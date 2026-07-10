package holyflame.administration.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Force la resolution (et donc la creation de session) du token CSRF des le debut
 * de la requete, avant que la reponse ne soit ecrite. Sans cela, sur les pages dont
 * le HTML est volumineux (ex: login.html avec sa config Tailwind inline), le buffer
 * de reponse peut se remplir et committer la reponse avant que Thymeleaf n'atteigne
 * le formulaire — la creation de session a ce moment-la echoue alors avec
 * IllegalStateException: "Cannot create a session after the response has been committed".
 */
public class CsrfTokenEagerLoadFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
