package com.cloudfuze.assessment.service;

import com.cloudfuze.assessment.domain.AttemptStatus;
import com.cloudfuze.assessment.domain.Role;
import com.cloudfuze.assessment.dto.Dtos;
import com.cloudfuze.assessment.entity.AppUser;
import com.cloudfuze.assessment.exception.ApiException;
import com.cloudfuze.assessment.repository.AppUserRepository;
import com.cloudfuze.assessment.repository.AttemptRepository;
import com.cloudfuze.assessment.security.AzureTokenVerifier;
import com.cloudfuze.assessment.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Microsoft sign-in, the one-time profile, and who counts as an admin. */
@Service
public class AuthService {

    private final AzureTokenVerifier verifier;
    private final JwtService jwt;
    private final AppUserRepository users;
    private final AttemptRepository attempts;
    private final com.cloudfuze.assessment.repository.AdminEmailRepository adminList;
    private final Set<String> bootstrapAdmins;
    private final Set<String> domains;

    public AuthService(AzureTokenVerifier verifier, JwtService jwt, AppUserRepository users,
                       AttemptRepository attempts,
                       com.cloudfuze.assessment.repository.AdminEmailRepository adminList,
                       @Value("${app.admin-emails:}") String adminEmails,
                       @Value("${app.allowed-domains:cloudfuze.com}") String allowedDomains) {
        this.verifier = verifier;
        this.jwt = jwt;
        this.users = users;
        this.attempts = attempts;
        this.adminList = adminList;
        this.bootstrapAdmins = split(adminEmails);
        this.domains = split(allowedDomains);
    }

    private static Set<String> split(String csv) {
        return Arrays.stream(csv == null ? new String[0] : csv.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    /**
     * THE ADMIN LIST LIVES IN THE DATABASE (table admin_email). APP_ADMIN_EMAILS only fills it the
     * very first time, when the table is empty; after that the table is the only source, so admins
     * are added or removed with a row, without touching settings or restarting.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void bootstrapAdmins() {
        if (adminList.count() > 0) return;
        for (String e : bootstrapAdmins) {
            com.cloudfuze.assessment.entity.AdminEmail a = new com.cloudfuze.assessment.entity.AdminEmail();
            a.setEmail(e);
            a.setAddedAt(Instant.now());
            adminList.save(a);
        }
    }

    public boolean isAdmin(String email) {
        return email != null && adminList.existsByEmailIgnoreCase(email.trim());
    }

    @Transactional
    public Dtos.LoginResponse login(String idToken) {
        Jwt token = verifier.verify(idToken);
        String email = firstNonBlank(token.getClaimAsString("preferred_username"), token.getClaimAsString("email"),
                token.getClaimAsString("upn"));
        return signIn(email, token.getClaimAsString("name"));
    }

    private Dtos.LoginResponse signIn(String rawEmail, String msName) {
        if (rawEmail == null || !rawEmail.contains("@")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Your Microsoft account has no email address.");
        }
        String email = rawEmail.trim();
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        if (!domains.isEmpty() && !domains.contains(domain)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Please sign in with your Neutara work account.");
        }
        AppUser u = users.findByEmailIgnoreCase(email).orElseGet(() -> {
            AppUser n = new AppUser();
            n.setEmail(email);
            n.setCreatedAt(Instant.now());
            return n;
        });
        u.setMicrosoftName(msName);
        // The list decides on every sign-in, so adding or removing an admin needs no data change.
        u.setRole(isAdmin(email) ? Role.ADMIN : Role.CANDIDATE);
        u.setLastLoginAt(Instant.now());
        users.save(u);
        return new Dtos.LoginResponse(jwt.issue(u), me(u));
    }

    public Dtos.Me me(AppUser u) {
        String status = attempts.findByUserId(u.getId())
                .map(a -> a.getStatus() == AttemptStatus.IN_PROGRESS ? "IN_PROGRESS" : "DONE")
                .orElse("NOT_STARTED");
        String name = u.getName() != null ? u.getName() : u.getMicrosoftName();
        return new Dtos.Me(u.getId(), u.getEmail(), name, u.getEmployeeId(), u.getTeam(), u.getJobRole(),
                u.getRole().name(), u.isProfileComplete(), status);
    }

    @Transactional
    public Dtos.Me saveProfile(AppUser u, Dtos.ProfileRequest r) {
        // Locked once the test has begun: the report must name the person who sat it.
        if (attempts.findByUserId(u.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Your details cannot be changed after the test has started.");
        }
        u.setName(clean(r.name()));
        u.setEmployeeId(clean(r.employeeId()).toUpperCase(Locale.ROOT));
        u.setTeam(matchExistingTeam(clean(r.team())));
        u.setJobRole(clean(r.jobRole()));
        u.setProfileComplete(true);
        users.save(u);
        return me(u);
    }

    /** "migration team" and "Migration Team" are one team, so reuse the spelling already on file. */
    private String matchExistingTeam(String team) {
        return users.findDistinctTeams().stream().filter(t -> t.equalsIgnoreCase(team)).findFirst().orElse(team);
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
