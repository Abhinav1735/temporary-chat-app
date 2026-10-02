package com.tempchat.server.security;

import com.tempchat.server.entity.User;
import com.tempchat.server.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CredentialsChangeFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public CredentialsChangeFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Login does not require authentication.
        // Credential change itself must remain accessible.
        if (path.equals("/api/auth/login")
                || path.equals("/api/auth/change-credentials")) {

            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() != null) {

            String loginId = authentication.getName();

            User user = userRepository.findByLoginId(loginId)
                    .orElse(null);

            if (user != null
                    && user.isMustChangeCredentials()) {

                response.setStatus(
                        HttpServletResponse.SC_FORBIDDEN
                );

                response.setContentType("application/json");

                response.getWriter().write(
                        """
                        {
                            "error": "CREDENTIAL_CHANGE_REQUIRED",
                            "message": "You must change your credentials before accessing this resource"
                        }
                        """
                );

                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}