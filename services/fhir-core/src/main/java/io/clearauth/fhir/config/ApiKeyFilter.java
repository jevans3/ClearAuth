package io.clearauth.fhir.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Optional shared-secret gate for non-public endpoints.
 *
 * When CLEARAUTH_APIKEY (property clearauth.api-key) is set, requests to
 * /fhir/** and /api/** must present a matching X-API-Key header. Static
 * dashboard assets, actuator health, and CDS discovery stay open. Unset (the
 * default) leaves the demo fully open. Production deployments should put a
 * real OAuth2/SMART-on-FHIR gateway in front; this filter is the minimum bar
 * for a shared environment.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final String apiKey;

    public ApiKeyFilter(@Value("${clearauth.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.startsWith("/fhir/") || path.startsWith("/api/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader("X-API-Key");
        if (presented != null && constantTimeEquals(presented, apiKey)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"missing or invalid X-API-Key\"}");
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
