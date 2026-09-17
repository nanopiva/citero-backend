package com.nanopiva.citero.security;

import com.nanopiva.citero.entity.User;
import com.nanopiva.citero.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link UserDetailsService} que carga usuarios desde la base de datos.
 *
 * Spring Security utiliza este servicio durante el proceso de autenticación
 * para obtener los datos del usuario a partir de su email (username).
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Carga un usuario por su email (utilizado como username en Spring Security).
     *
     * @param email el email del usuario a cargar
     * @return UserDetails con los datos del usuario y sus autoridades
     * @throws UsernameNotFoundException si no se encuentra un usuario con ese email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado con email: " + email));

        return UserDetailsImpl.build(user);
    }

    /**
     * Carga un usuario por su ID. Útil para reconstruir la autenticación
     * a partir del claim "sub" del JWT.
     *
     * @param id el ID del usuario
     * @return UserDetails con los datos del usuario
     * @throws UsernameNotFoundException si no se encuentra un usuario con ese ID
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) throws UsernameNotFoundException {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado con ID: " + id));

        return UserDetailsImpl.build(user);
    }
}
