package com.pedidos360.orders.security;

import java.util.Set;

/** Datos del usuario autenticado, extraídos del JWT por {@link UsuarioActual}. Actores: Operador y Cliente. */
public record Usuario(String id, String nombre, String email, Set<String> roles) {

    public static final String OPERADOR = "Operador";
    public static final String CLIENTE = "Cliente";

    public Usuario {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** Operador: ve y gestiona todos los pedidos. */
    public boolean esPersonal() {
        return roles.contains(OPERADOR);
    }

    /** Solo ve/gestiona sus propios pedidos. */
    public boolean esSoloCliente() {
        return !esPersonal();
    }

    /** Rol con más privilegios, para el historial de estados. */
    public String rolPrincipal() {
        if (roles.contains(OPERADOR)) {
            return OPERADOR;
        }
        return roles.contains(CLIENTE) ? CLIENTE : null;
    }
}
