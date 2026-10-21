package com.pedidos360.orders.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.pedidos360.orders.exception.EstadoInvalidoException;
import com.pedidos360.orders.exception.TransicionInvalidaException;

class MaquinaEstadosPedidoTest {

    /** Especificación del enunciado, escrita aparte de la implementación. */
    private static final Set<String> VALIDAS = Set.of(
            "CREADO->ACEPTADO", "CREADO->CANCELADO",
            "ACEPTADO->EN_PREPARACION", "ACEPTADO->CANCELADO",
            "EN_PREPARACION->DESPACHADO", "EN_PREPARACION->CANCELADO",
            "DESPACHADO->ENTREGADO");

    private final MaquinaEstadosPedido maquina = new MaquinaEstadosPedido();

    /** Las 36 combinaciones (6 x 6), válidas e inválidas. */
    static List<Arguments> todasLasTransiciones() {
        List<Arguments> casos = new ArrayList<>();
        for (EstadoPedido actual : EstadoPedido.values()) {
            for (EstadoPedido nuevo : EstadoPedido.values()) {
                casos.add(Arguments.of(actual, nuevo, VALIDAS.contains(actual + "->" + nuevo)));
            }
        }
        return casos;
    }

    @ParameterizedTest(name = "{0} -> {1} válida={2}")
    @MethodSource("todasLasTransiciones")
    void cadaTransicionSeComportaSegunElEnunciado(EstadoPedido actual, EstadoPedido nuevo, boolean valida) {
        assertThat(maquina.esValida(actual, nuevo)).isEqualTo(valida);
        if (valida) {
            assertThatCode(() -> maquina.validar(actual, nuevo)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> maquina.validar(actual, nuevo))
                    .isInstanceOf(TransicionInvalidaException.class)
                    .hasMessageContaining(actual.name());
        }
    }

    @Test
    void noSePuedeDespacharUnPedidoNoAceptado() {
        assertThatThrownBy(() -> maquina.validar(EstadoPedido.CREADO, EstadoPedido.DESPACHADO))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("No se puede despachar un pedido que no fue aceptado");
    }

    @Test
    void estadosTerminalesNoTienenSiguientes() {
        assertThat(maquina.siguientes(EstadoPedido.ENTREGADO)).isEmpty();
        assertThat(maquina.siguientes(EstadoPedido.CANCELADO)).isEmpty();
        assertThatThrownBy(() -> maquina.validar(EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO))
                .hasMessageContaining("estado final");
    }

    @Test
    void nullNoEsValido() {
        assertThat(maquina.esValida(null, EstadoPedido.ACEPTADO)).isFalse();
        assertThat(maquina.esValida(EstadoPedido.CREADO, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"EN_PREPARACIÓN", "EN_PREPARACION", "en_preparación", " En Preparación "})
    void estadoAceptaAliasConTilde(String valor) {
        assertThat(EstadoPedido.desde(valor)).isEqualTo(EstadoPedido.EN_PREPARACION);
    }

    @Test
    void estadoDesconocidoLanzaEstadoInvalido() {
        assertThatThrownBy(() -> EstadoPedido.desde("VOLANDO")).isInstanceOf(EstadoInvalidoException.class);
        assertThatThrownBy(() -> EstadoPedido.desde(" ")).isInstanceOf(EstadoInvalidoException.class);
    }
}
