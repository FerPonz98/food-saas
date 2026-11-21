package com.sistema.pedidosCori.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dto.NotificacionWsDTO;
import com.sistema.pedidosCori.models.dto.PedidoRequestDTO;
import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.Pedido;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComandaService {
    private final IPedidoService pedidoService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConfiguracionNegocioService configuracion;

    @Transactional
    public Pedido crear(PedidoRequestDTO dto) {
        Pedido pedido = pedidoService.crearPedido(dto);
        NotificacionWsDTO notif = NotificacionWsDTO.builder()
            .pedidoId(pedido.getId()).numeroMesa(pedido.getNumeroMesa())
            .nombreCliente(pedido.getNombreCliente()).estado(EstadoPedido.PENDIENTE)
            .mensaje("Nuevo pedido — " + etiqueta(pedido)).tipo("NUEVO_PEDIDO").build();
        messagingTemplate.convertAndSend("/topic/cocina", notif);
        messagingTemplate.convertAndSend("/topic/mesero", notif);
        return pedido;
    }

    @Transactional
    public Pedido agregar(Long id, List<PedidoRequestDTO.DetalleRequestDTO> nuevosDetalles) {
        Pedido saved = pedidoService.agregarDetalles(id, nuevosDetalles);
        NotificacionWsDTO notif = NotificacionWsDTO.builder()
            .pedidoId(saved.getId()).numeroMesa(saved.getNumeroMesa())
            .nombreCliente(saved.getNombreCliente()).estado(saved.getEstado())
            .mensaje("Extras agregados — " + etiqueta(saved)).tipo("ITEMS_AGREGADOS").build();
        messagingTemplate.convertAndSend("/topic/cocina", notif);
        messagingTemplate.convertAndSend("/topic/mesero", notif);
        return saved;
    }

    public String etiqueta(Pedido p) {
        return p.getNumeroMesa() != null
            ? configuracion.vista().getPuntoAtencionSingular() + " " + p.getNumeroMesa()
            : p.getNombreCliente();
    }
}
