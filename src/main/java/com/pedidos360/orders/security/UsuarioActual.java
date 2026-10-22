package com.pedidos360.orders.security;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Lee el usuario del JWT de Entra ID: id = "oid" (estable por tenant; "sub" si no viene),
 * nombre = "name", email = "email" (o "preferred_username") y roles desde las authorities ROLE_x
 * que arma SecurityConfig a partir del claim "roles". "email" es el campo Email del usuario en Entra ID
 * (claim opcional del access token); si no viene, se usa el usuario de inicio de sesión.
 */
@Component
public class UsuarioActual {

    private static final String ROLE_PREFIX = "ROLE_";

    public Usuario obtener() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new AuthenticationCredentialsNotFoundException("No hay un JWT autenticado");
        }
        Jwt jwt = jwtAuth.getToken();
        String id = primero(jwt.getClaimAsString("oid"), jwt.getSubject());
        String email = primero(jwt.getClaimAsString("email"), jwt.getClaimAsString("preferred_username"));
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(ROLE_PREFIX))
                .map(a -> a.substring(ROLE_PREFIX.length()))
                .collect(Collectors.toSet());
        return new Usuario(id, jwt.getClaimAsString("name"), email, roles);
    }

    private static String primero(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
