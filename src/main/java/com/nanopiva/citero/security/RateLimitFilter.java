package com.nanopiva.citero.security;

import com.nanopiva.citero.util.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Aplica límites de tasa a los endpoints sensibles (login, registro, reseteo de
 * contraseña y OTP) para mitigar fuerza bruta y email bombing.
 */
@Component
@Order(-190)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String TOO_MANY_REQUESTS_MESSAGE =
            "Demasiadas solicitudes. Esperá un momento e intentá de nuevo.";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<Rule> RULES = List.of(
            new Rule("POST", "/api/auth/login", 10, Duration.ofMinutes(1)),
            new Rule("POST", "/api/auth/register", 5, Duration.ofMinutes(1)),
            new Rule("POST", "/api/auth/register/request-otp", 5, Duration.ofMinutes(15)),
            new Rule("POST", "/api/auth/forgot-password", 5, Duration.ofMinutes(15)),
            new Rule("POST", "/api/auth/reset-password", 10, Duration.ofMinutes(15)),
            new Rule("POST", "/api/otp/send", 5, Duration.ofMinutes(15)),
            new Rule("POST", "/api/otp/verify", 10, Duration.ofMinutes(15)),
            // OTP de cancelación público (anti email-bombing).
            new Rule("POST", "/api/appointments/public/*/send-cancellation-otp", 3, Duration.ofMinutes(15)),
            // Reserva pública (turnos basura / email bombing / llenar la agenda).
            new Rule("POST", "/api/appointments", 10, Duration.ofMinutes(15)),
            // Disponibilidad: protege CPU/DB ante floods.
            new Rule("GET", "/api/availability", 60, Duration.ofMinutes(1)),
            // Invitaciones de staff (anti email-bombing).
            new Rule("POST", "/api/businesses/*/staff", 20, Duration.ofMinutes(15)),
            new Rule("POST", "/api/businesses/*/staff/*/resend-invitation", 20, Duration.ofMinutes(15)),
            // Backstop global por IP.
            new Rule("*", "/**", 300, Duration.ofMinutes(1))
    );

    private final RateLimitStore rateLimitStore;
    private final ClientIpResolver clientIpResolver;

    @Value("${citero.rate-limit.enabled:true}")
    private boolean enabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        // Se aplican TODAS las reglas que matchean (la específica y el backstop global).
        String clientIp = clientIpResolver.resolve(request);
        for (Rule rule : RULES) {
            if (matches(rule, request)
                    && !rateLimitStore.tryConsume(rule.path() + ":" + clientIp, rule.limit(), rule.window())) {
                log.warn("security_event=rate_limit_exceeded method={} path={} ip={} rule={}",
                        request.getMethod(), request.getRequestURI(), clientIp, rule.path());
                writeTooManyRequests(request, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean matches(Rule rule, HttpServletRequest request) {
        return ("*".equals(rule.method()) || rule.method().equals(request.getMethod()))
                && PATH_MATCHER.match(rule.path(), request.getRequestURI());
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response) throws IOException {
        JsonErrorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests",
                TOO_MANY_REQUESTS_MESSAGE, request.getRequestURI());
    }

    private record Rule(String method, String path, int limit, Duration window) {
    }
}
