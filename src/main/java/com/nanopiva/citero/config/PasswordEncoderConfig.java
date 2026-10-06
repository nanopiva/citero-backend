package com.nanopiva.citero.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Fuerza 12; no afecta hashes previos (el costo va dentro del propio hash).
        return new BCryptPasswordEncoder(12);
    }
}