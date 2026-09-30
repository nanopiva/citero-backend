package com.nanopiva.citero.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyLimitFilterTest {

    @Test
    void rechazaCuandoSeSuperaLaConcurrencia() throws Exception {
        ConcurrencyLimitFilter filter = new ConcurrencyLimitFilter(1, 1);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        FilterChain blockingChain = (request, response) -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        Thread holder = new Thread(() -> {
            try {
                filter.doFilter(new MockHttpServletRequest("GET", "/api/availability"), firstResponse, blockingChain);
            } catch (Exception ignored) {
                // se libera abajo
            }
        });
        holder.start();

        assertTrue(entered.await(5, TimeUnit.SECONDS), "El primer request debe tomar el permiso");

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/availability"), secondResponse, (request, response) -> {
        });

        assertEquals(429, secondResponse.getStatus(),
                "Un segundo request concurrente sobre el mismo bulkhead debe rechazarse (429)");

        release.countDown();
        holder.join(5000);
    }

    @Test
    void noLimitaEndpointsNoConfigurados() throws Exception {
        ConcurrencyLimitFilter filter = new ConcurrencyLimitFilter(1, 1);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/businesses/foo"), response,
                (request, reply) -> ((MockHttpServletResponse) reply).setStatus(200));

        assertEquals(200, response.getStatus());
    }
}
