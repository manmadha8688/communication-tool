package com.cloudfuze.assessment.security;

import com.cloudfuze.assessment.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies a Microsoft ID token: signature against the tenant's keys, expiry, issuer, and that
 * it was issued to THIS application. Copied from AI Comm Trainer, where it is proven.
 */
@Component
public class AzureTokenVerifier {

    private final String tenantId;
    private final String clientId;
    private volatile JwtDecoder decoder;

    public AzureTokenVerifier(@Value("${app.azure.tenant-id:}") String tenantId,
                              @Value("${app.azure.client-id:}") String clientId) {
        this.tenantId = tenantId;
        this.clientId = clientId;
    }

    public Jwt verify(String idToken) {
        try {
            return decoder().decode(idToken);
        } catch (JwtException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Microsoft sign-in could not be verified. Please sign in again.");
        }
    }

    private JwtDecoder decoder() {
        JwtDecoder local = decoder;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (decoder == null) {
                if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(clientId)) {
                    throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "Microsoft sign-in is not configured on the server.");
                }
                String issuer = "https://login.microsoftonline.com/" + tenantId + "/v2.0";
                NimbusJwtDecoder nimbus = NimbusJwtDecoder
                        .withJwkSetUri("https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys")
                        .build();
                List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
                validators.add(JwtValidators.createDefaultWithIssuer(issuer));
                validators.add(token -> token.getAudience() != null && token.getAudience().contains(clientId)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience",
                                "Token was not issued to this application", null)));
                nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
                decoder = nimbus;
            }
            return decoder;
        }
    }
}
