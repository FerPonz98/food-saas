package com.sistema.pedidosCori.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPresentacionPlatoDao;
import com.sistema.pedidosCori.models.dto.NotificacionWsDTO;
import com.sistema.pedidosCori.models.dto.PedidoRequestDTO;
import com.sistema.pedidosCori.models.dto.StockUpdateDTO;
import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.Pedido;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComandaService {
    private final IPedidoService pedidoService;
    private final IPlatoDao platoDao;
    private final IPresentacionPlatoDao presentacionPlatoDao;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConfiguracionNegocioService configuracion;

    @Transactional
    public Pedido crear(PedidoRequestDTO dto) {
        Pedido pedido = pedidoService.crearPedido(dto);
        Map<Long, Integer> descPlato = new HashMap<>(), descPres = new HashMap<>(), descSopa = new HashMap<>();
        pedido.getDetalles().forEach(d -> {
            if (d.getPresentacion() != null) descPres.merge(d.getPresentacion().getId(), d.getCantidad(), Integer::sum);
            else descPlato.merge(d.getPlato().getId(), d.getCantidad(), Integer::sum);
            if (d.getAcompananteSopa() != null) descSopa.merge(d.getAcompananteSopa().getId(), d.getCantidad(), Integer::sum);
        });
        aplicarDescuentoStock(descPlato, descPres, descSopa);
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
        Map<Long, Integer> descPlato = new HashMap<>(), descPres = new HashMap<>(), descSopa = new HashMap<>();
        nuevosDetalles.forEach(d -> {
            if (d.getPresentacionId() != null) descPres.merge(d.getPresentacionId(), d.getCantidad(), Integer::sum);
            else descPlato.merge(d.getPlatoId(), d.getCantidad(), Integer::sum);
            if (d.getAcompananteSopaId() != null) descSopa.merge(d.getAcompananteSopaId(), d.getCantidad(), Integer::sum);
        });
        aplicarDescuentoStock(descPlato, descPres, descSopa);
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

    private void aplicarDescuentoStock(Map<Long, Integer> descPlato, Map<Long, Integer> descPres, Map<Long, Integer> descSopa) {
        descPlato.forEach((pid, q) -> platoDao.findById(pid).ifPresent(p -> {
            if (p.getStockDisponible() == null) return;
            if (platoDao.descontarStockPreciso(pid, q) == 0)
                throw new IllegalStateException("Stock insuficiente para el plato: " + p.getNombre());
            messagingTemplate.convertAndSend("/topic/stock", StockUpdateDTO.builder().platoId(p.getId())
                .stockRestante(p.getStockDisponible() - q).nombrePlato(p.getNombre()).build());
        }));
        descPres.forEach((presId, q) -> presentacionPlatoDao.findById(presId).ifPresent(pres -> {
            if (pres.getStockDisponible() == null) return;
            if (presentacionPlatoDao.descontarStockPreciso(presId, q) == 0)
                throw new IllegalStateException("Stock insuficiente para el tamaño: " + pres.getNombre() + " de " + pres.getPlato().getNombre());
            messagingTemplate.convertAndSend("/topic/stock", StockUpdateDTO.builder().platoId(pres.getPlato().getId())
                .presentacionId(pres.getId()).stockRestante(pres.getStockDisponible() - q)
                .nombrePlato(pres.getPlato().getNombre() + " – " + pres.getNombre()).build());
        }));
        descSopa.forEach((sid, q) -> platoDao.findById(sid).ifPresent(s -> {
            if (s.getStockDisponible() == null) return;
            if (platoDao.descontarStockPreciso(sid, q) == 0)
                throw new IllegalStateException("Stock insuficiente de la sopa: " + s.getNombre());
            messagingTemplate.convertAndSend("/topic/stock", StockUpdateDTO.builder().platoId(s.getId())
                .stockRestante(s.getStockDisponible() - q).nombrePlato(s.getNombre()).build());
        }));
    }
}
