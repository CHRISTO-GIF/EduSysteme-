package holyflame.administration.config;

import holyflame.administration.repository.UtilisateurRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final int MAX_TENTATIVES = 5;
    private static final long VERROUILLAGE_MINUTES = 15;

    @Autowired private UtilisateurRepository utilisateurRepository;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        if (exception instanceof LockedException) {
            response.sendRedirect("/login?locked");
            return;
        }

        String email = request.getParameter("username");
        if (email != null && !email.isBlank()) {
            utilisateurRepository.findByEmail(email.trim()).ifPresent(u -> {
                u.setTentativesEchouees(u.getTentativesEchouees() + 1);
                if (u.getTentativesEchouees() >= MAX_TENTATIVES) {
                    u.setVerrouilleJusqua(LocalDateTime.now().plusMinutes(VERROUILLAGE_MINUTES));
                    u.setTentativesEchouees(0);
                }
                utilisateurRepository.save(u);
            });
        }
        response.sendRedirect("/login?error");
    }
}
