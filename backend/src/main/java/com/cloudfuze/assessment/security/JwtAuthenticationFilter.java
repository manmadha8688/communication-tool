package com.cloudfuze.assessment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Reads the Bearer token on each request; an invalid one simply leaves the caller anonymous (401). */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final com.cloudfuze.assessment.repository.AdminEmailRepository adminList;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   com.cloudfuze.assessment.repository.AdminEmailRepository adminList) {
        this.jwtService = jwtService;
        this.adminList = adminList;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AppPrincipal t = jwtService.parse(header.substring(7));
                // Admin rights come from the admin_email table NOW, not from when the token was issued.
                com.cloudfuze.assessment.domain.Role role = adminList.existsByEmailIgnoreCase(t.email())
                        ? com.cloudfuze.assessment.domain.Role.ADMIN : com.cloudfuze.assessment.domain.Role.CANDIDATE;
                AppPrincipal p = new AppPrincipal(t.userId(), t.email(), role);
                var auth = new UsernamePasswordAuthenticationToken(p, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + p.role().name())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
