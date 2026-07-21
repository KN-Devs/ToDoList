package com.todolist.portfolio.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ObservabilityConfig {

    /**
     * Enregistré via FilterRegistrationBean (plutôt que @Component) pour
     * s'exécuter avant la chaîne de filtres de Spring Security : l'identifiant
     * de corrélation doit être présent dans le MDC dès les tout premiers logs
     * de la requête, y compris ceux de l'authentification.
     */
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
