package com.sistema.pedidosCori.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistema.pedidosCori.service.CobranzaService;
import com.sistema.pedidosCori.service.NegocioActual;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
class CobrosSuperAdminController {
    private final CobranzaService cobranza;

    record Anulacion(String motivo) {
    }

    @GetMapping("/cobros/parametros")
    CobranzaService.Parametros parametros() { return cobranza.parametros(); }

    @PutMapping("/cobros/parametros")
    CobranzaService.Parametros actualizarParametros(@RequestBody CobranzaService.Parametros p) {
        return cobranza.actualizarParametros(p);
    }

    @GetMapping("/cobros/precios")
    List<CobranzaService.PrecioModulo> precios() { return cobranza.preciosModulos(); }

    @PutMapping("/cobros/precios/{modulo}")
    CobranzaService.PrecioModulo actualizarPrecio(@PathVariable String modulo, @RequestBody CobranzaService.DatosPrecio d) {
        return cobranza.actualizarPrecio(modulo, d);
    }

    @GetMapping("/facturas")
    List<CobranzaService.FacturaVista> facturas(@RequestParam(required = false) String estado) {
        return cobranza.facturas(estado);
    }

    @GetMapping("/negocios/{id}/cobro")
    CobranzaService.EstadoCobro estado(@PathVariable Long id) { return cobranza.estado(id); }

    @PostMapping("/negocios/{id}/facturas")
    ResponseEntity<CobranzaService.FacturaVista> emitir(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cobranza.emitir(id));
    }

    @PutMapping("/facturas/{id}/pago")
    CobranzaService.FacturaVista pagar(@PathVariable Long id, @RequestBody CobranzaService.Pago pago) {
        return cobranza.registrarPago(id, pago);
    }

    @PutMapping("/facturas/{id}/anular")
    CobranzaService.FacturaVista anular(@PathVariable Long id, @RequestBody(required = false) Anulacion r) {
        return cobranza.anular(id, r == null ? null : r.motivo());
    }

    @PostMapping("/cobros/ejecutar")
    Map<String, Object> ejecutar() {
        CobranzaService.ResultadoCobranza r = cobranza.ejecutar(LocalDate.now());
        return Map.of("facturasEmitidas", r.facturasEmitidas(), "avisosEnviados", r.avisosEnviados(),
            "negociosSuspendidos", r.negociosSuspendidos());
    }
}

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
class CobrosNegocioController {
    private final CobranzaService cobranza;

    @GetMapping("/facturacion")
    CobranzaService.EstadoCobro estado() {
        return cobranza.estado(NegocioActual.requerido());
    }
}
