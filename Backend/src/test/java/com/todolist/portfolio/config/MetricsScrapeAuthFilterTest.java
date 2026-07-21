package com.todolist.portfolio.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MetricsScrapeAuthFilterTest {

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAsAdminWhenTheTokenMatches() throws Exception {
        MetricsScrapeAuthFilter filter = new MetricsScrapeAuthFilter("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer secret-token");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void doesNothingWhenTheTokenDoesNotMatch() throws Exception {
        MetricsScrapeAuthFilter filter = new MetricsScrapeAuthFilter("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer wrong-token");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doesNothingWhenNoScrapeTokenIsConfigured() throws Exception {
        MetricsScrapeAuthFilter filter = new MetricsScrapeAuthFilter("");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer anything");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doesNothingForADifferentPathEvenWithAMatchingToken() throws Exception {
        MetricsScrapeAuthFilter filter = new MetricsScrapeAuthFilter("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/metrics");
        request.addHeader("Authorization", "Bearer secret-token");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
