package com.nanopiva.citero.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nanopiva.citero.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Implementación de {@link UserDetails} que envuelve una entidad {@link User}
 * para integrarla con el framework de Spring Security.
 *
 * Proporciona las autoridades (roles), credenciales y estado de la cuenta
 * necesarios para la autenticación y autorización.
 */
@Data
@AllArgsConstructor
public class UserDetailsImpl implements UserDetails {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String email;

    @JsonIgnore
    private String password;

    private String phone;
    private Collection<? extends GrantedAuthority> authorities;

    /**
     * Construye un UserDetailsImpl a partir de una entidad User.
     *
     * @param user la entidad User cargada desde la base de datos
     * @return una instancia de UserDetailsImpl lista para usar en Spring Security
     */
    public static UserDetailsImpl build(User user) {
        return new UserDetailsImpl(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getPhone(),
                Collections.emptyList() // Lista vacía de autoridades globales
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
