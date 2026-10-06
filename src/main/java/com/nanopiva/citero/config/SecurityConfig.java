package com.nanopiva.citero.config;

import com.nanopiva.citero.security.jwt.JwtAuthenticationFilter;
import com.nanopiva.citero.util.JsonErrorWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsOrigins corsOrigins;

    // La consola H2 solo se habilita en el perfil dev; si está apagada, ni se permite en
    // seguridad ni se relaja frame-options.
    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    // Swagger/OpenAPI queda público sólo si está habilitado (en prod está deshabilitado).
    @Value("${springdoc.swagger-ui.enabled:false}")
    private boolean swaggerEnabled;

    // Fuerza el header HSTS aunque la app vea HTTP (TLS termina en el proxy de Railway).
    @Value("${citero.security.hsts.force:false}")
    private boolean forceHsts;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/**").permitAll()
                            .requestMatchers("/api/otp/**").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/businesses/my-businesses").authenticated()
                            .requestMatchers(HttpMethod.GET, "/api/businesses/{slug}").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/businesses/{businessId}/services").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/businesses/{businessId}/staff").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/businesses/{businessId}/config").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/availability", "/api/availability/**").permitAll()
                            // Sólo se publican POSTs concretos (no todo /api/appointments/**, para no
                            // dejar públicas subrutas POST futuras por defecto).
                            .requestMatchers(HttpMethod.POST, "/api/appointments").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/appointments/public/*/send-cancellation-otp").permitAll()
                            // Endpoints públicos para gestión de turnos (cancelación sin login)
                            .requestMatchers(HttpMethod.GET, "/api/appointments/public/**").permitAll()
                            .requestMatchers(HttpMethod.PUT, "/api/appointments/public/**").permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**").permitAll();

                    // Swagger/OpenAPI sólo público si springdoc está habilitado (dev).
                    if (swaggerEnabled) {
                        auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    }
                    if (h2ConsoleEnabled) {
                        auth.requestMatchers("/h2-console/**").permitAll();
                    }

                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        // Sin token válido => 401 (no 403) con el formato de error de la app.
                        .authenticationEntryPoint((request, response, authException) ->
                                JsonErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
                                        "No estás autenticado. Iniciá sesión.", request.getRequestURI()))
                        // Autenticado pero sin permiso => 403.
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                JsonErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
                                        "No tenés permiso para acceder a este recurso.", request.getRequestURI())))
                .headers(headers -> {
                    headers.frameOptions(frameOptions -> {
                        if (h2ConsoleEnabled) {
                            frameOptions.sameOrigin();
                        } else {
                            frameOptions.deny();
                        }
                    });
                    headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER));
                    headers.permissionsPolicyHeader(permissions -> permissions.policy(
                            "camera=(), microphone=(), geolocation=(), payment=(), usb=()"));
                    // HSTS explícito (Spring sólo lo emite en requests "secure"; detrás del
                    // proxy TLS la app ve HTTP, por eso se fuerza cuando citero.security.hsts.force=true).
                    headers.httpStrictTransportSecurity(hsts -> {
                        hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(63072000);
                        if (forceHsts) {
                            hsts.requestMatcher(AnyRequestMatcher.INSTANCE);
                        }
                    });
                    headers.addHeaderWriter(
                            new StaticHeadersWriter("X-Permitted-Cross-Domain-Policies", "none"));
                    headers.addHeaderWriter(
                            new StaticHeadersWriter("Cross-Origin-Resource-Policy", "same-site"));
                });
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) {
        try {
            return authConfig.getAuthenticationManager();
        } catch (Exception e) {
            throw new RuntimeException("Error al obtener AuthenticationManager", e);
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsOrigins.list());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Business-ID"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // CORS solo para el API (el frontend solo llama a /api/**). Así otras rutas
        // del backend (p. ej. /h2-console en dev) no pasan por el filtro CORS y no
        // se rechazan como "Invalid CORS request".
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}