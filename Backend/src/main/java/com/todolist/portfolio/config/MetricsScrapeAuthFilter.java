package com.todolist.portfolio.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authentifie un scraper Prometheus sur /actuator/prometheus via un jeton
 * statique dédié (en-tête Authorization: Bearer <jeton>), distinct des
 * comptes ADMIN qui continuent d'y accéder normalement par JWT. Un scraper
 * automatisé ne peut pas raisonnablement gérer un access token expirant
 * toutes les 15 minutes ; ce jeton est désactivé par défaut
 * (METRICS_SCRAPE_TOKEN non défini) et n'est utilisé qu'en local pour la
 * démo Prometheus/Grafana, voir docker-compose.observability.yml.
 *
 * Placé avant JwtAuthenticationFilter dans la chaîne : si le jeton
 * correspond, l'authentification est déjà posée quand JwtAuthenticationFilter
 * s'exécute, qui ne retente alors pas de le décoder comme un JWT (il ne
 * touche jamais à une authentification déjà présente).
 */
public class MetricsScrapeAuthFilter extends OncePerRequestFilter {

    private static final String SCRAPE_PATH = "/actuator/prometheus";

    private final String scrapeToken;

    public MetricsScrapeAuthFilter(String scrapeToken) {
        this.scrapeToken = scrapeToken;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (scrapeToken != null && !scrapeToken.isBlank()
                && SCRAPE_PATH.equals(request.getRequestURI())
                && authHeader != null
                && authHeader.equals("Bearer " + scrapeToken)) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "prometheus-scraper", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
