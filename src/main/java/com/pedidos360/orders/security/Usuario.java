package com.pedidos360.orders.security;

import java.util.Set;

/** Datos del usuario autenticado, extraídos del JWT por {@link UsuarioActual}. */
public record Usuario(String id, String nombre, String email, Set<String> roles) {

    public static final String ADMIN = "Admin";
    public static final String OPERADOR = "Operador";
    public static final String CLIENTE = "Cliente";

    public Usuario {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** Admin u Operador: ve y gestiona todos los pedidos. */
    public boolean esPersonal() {
        return roles.contains(ADMIN) || roles.contains(OPERADOR);
    }

    /** Solo ve/gestiona sus propios pedidos. */
    public boolean esSoloCliente() {
        return !esPersonal();
    }

    /** Rol con más privilegios, para el historial de estados. */
    public String rolPrincipal() {
        if (roles.contains(ADMIN)) {
            return ADMIN;
        }
        if (roles.contains(OPERADOR)) {
            return OPERADOR;
        }
        return roles.contains(CLIENTE) ? CLIENTE : null;
    }
}
