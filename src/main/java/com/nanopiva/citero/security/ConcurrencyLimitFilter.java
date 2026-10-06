package com.nanopiva.citero.security;

import com.nanopiva.citero.util.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Bulkhead: limita la concurrencia de operaciones caras (disponibilidad y reserva). No depende
 * de la IP, así que también amortigua floods distribuidos; si se satura responde 429.
 */
@Component
@Order(-180)
@Slf4j
public class ConcurrencyLimitFilter extends OncePerRequestFilter {

    private static final String TOO_MANY_REQUESTS_MESSAGE =
            "El servidor está ocupado. Esperá unos segundos e intentá de nuevo.";

    private final Semaphore availabilitySemaphore;
    private final Semaphore bookingSemaphore;

    public ConcurrencyLimitFilter(
            @Value("${citero.concurrency.availability.max:20}") int availabilityMax,
            @Value("${citero.concurrency.booking.max:10}") int bookingMax) {
        this.availabilitySemaphore = new Semaphore(Math.max(1, availabilityMax), true);
        this.bookingSemaphore = new Semaphore(Math.max(1, bookingMax), true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Semaphore semaphore = semaphoreFor(request);
        if (semaphore == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!semaphore.tryAcquire()) {
            log.warn("security_event=concurrency_limit_exceeded method={} path={}",
                    request.getMethod(), request.getRequestURI());
            writeTooManyRequests(request, response);
            return;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            semaphore.release();
        }
    }

    private Semaphore semaphoreFor(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        if ("GET".equals(method) && "/api/availability".equals(uri)) {
            return availabilitySemaphore;
        }
        if ("POST".equals(method) && "/api/appointments".equals(uri)) {
            return bookingSemaphore;
        }
        return null;
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JsonErrorWriter.write(response, 429, "Too Many Requests",
                TOO_MANY_REQUESTS_MESSAGE, request.getRequestURI());
    }
}
