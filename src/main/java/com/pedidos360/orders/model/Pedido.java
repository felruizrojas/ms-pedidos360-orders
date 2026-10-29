package com.pedidos360.orders.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// @Getter/@Setter en vez de @Data: con relaciones bidireccionales, el equals/hashCode/toString
// generados recorrerían items -> pedido -> items... en bucle.
@Entity
@Table(name = "pedido", indexes = {
        @Index(name = "idx_pedido_cliente", columnList = "clienteId"),
        @Index(name = "idx_pedido_creado_en", columnList = "creadoEn")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Claim "oid" del JWT de Entra ID (o "sub" si no viene). */
    @Column(nullable = false, length = 100)
    private String clienteId;

    @Column(length = 150)
    private String clienteNombre;

    @Column(length = 150)
    private String clienteEmail;

    @Column(nullable = false, length = 200)
    private String direccionEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    /** true entre ACEPTADO y CANCELADO: indica si al cancelar hay que reponer stock en catalog. */
    @Column(nullable = false)
    private boolean stockDescontado;

    @Column(nullable = false)
    private Instant creadoEn;
    private Instant aceptadoEn;
    private Instant enPreparacionEn;
    private Instant despachadoEn;
    private Instant entregadoEn;
    private Instant canceladoEn;

    @Column(length = 500)
    private String motivoCancelacion;

    /** Concurrencia optimista: dos operadores cambiando el mismo pedido a la vez -> el segundo recibe 409. */
    @Version
    private Long version;

    /** Borrado lógico (DELETE solo lo marca): el pedido se sigue viendo, en solo lectura. */
    @Column(nullable = false)
    private boolean eliminado;

    private Instant eliminadoEn;

    /** Nombre y rol de quien lo eliminó, p. ej. "Olga Operadora (Operador)". */
    @Column(length = 200)
    private String eliminadoPor;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<ItemPedido> items = new ArrayList<>();

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL)
    @OrderBy("fecha ASC, id ASC")
    @Builder.Default
    private List<HistorialEstado> historial = new ArrayList<>();

    public void agregarItem(ItemPedido item) {
        item.setPedido(this);
        items.add(item);
    }

    public void reemplazarItems(List<ItemPedido> nuevos) {
        items.clear();
        nuevos.forEach(this::agregarItem);
        recalcularTotal();
    }

    public void recalcularTotal() {
        total = items.stream().map(ItemPedido::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void agregarHistorial(HistorialEstado registro) {
        registro.setPedido(this);
        historial.add(registro);
    }

    /** Cambia el estado y deja el timestamp correspondiente. La validez la decide MaquinaEstadosPedido. */
    public void aplicarEstado(EstadoPedido nuevo, Instant fecha) {
        estado = nuevo;
        switch (nuevo) {
            case CREADO -> creadoEn = fecha;
            case ACEPTADO -> aceptadoEn = fecha;
            case EN_PREPARACION -> enPreparacionEn = fecha;
            case DESPACHADO -> despachadoEn = fecha;
            case ENTREGADO -> entregadoEn = fecha;
            case CANCELADO -> canceladoEn = fecha;
        }
    }
}
