package com.sistema.pedidosCori.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistema.pedidosCori.service.PlataformaService;

import lombok.RequiredArgsConstructor;

@Controller
public class SuperAdminController {
    @GetMapping("/superadmin")
    public String inicio() { return "superadmin/negocios"; }

    @GetMapping("/superadmin/planes")
    public String planes() { return "superadmin/planes"; }

    @GetMapping("/superadmin/cobros")
    public String cobros() { return "superadmin/cobros"; }
}

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
class SuperAdminApiController {
    private final PlataformaService plataforma;

    @GetMapping("/modulos")
    List<PlataformaService.ModuloVista> modulos() { return plataforma.modulosDisponibles(); }

    @GetMapping("/planes")
    List<PlataformaService.PlanVista> planes() { return plataforma.planes(); }

    @PutMapping("/planes/{codigo}")
    PlataformaService.PlanVista actualizarPlan(@PathVariable String codigo, @RequestBody PlataformaService.DatosPlan datos) {
        return plataforma.actualizarPlan(codigo, datos);
    }

    @GetMapping("/negocios")
    List<PlataformaService.NegocioVista> negocios() { return plataforma.negocios(); }

    @GetMapping("/negocios/{id}")
    PlataformaService.NegocioVista negocio(@PathVariable Long id) { return plataforma.negocio(id); }

    @PostMapping("/negocios")
    ResponseEntity<PlataformaService.AccesoTemporal> crear(@RequestBody PlataformaService.NuevoNegocio datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plataforma.crearNegocio(datos));
    }

    @PutMapping("/negocios/{id}")
    PlataformaService.NegocioVista actualizar(@PathVariable Long id, @RequestBody PlataformaService.DatosNegocio datos) {
        return plataforma.actualizarDatos(id, datos);
    }

    @PutMapping("/negocios/{id}/suscripcion")
    PlataformaService.NegocioVista suscripcion(@PathVariable Long id, @RequestBody PlataformaService.Suscripcion datos) {
        return plataforma.cambiarSuscripcion(id, datos);
    }

    @PutMapping("/negocios/{id}/modulos")
    PlataformaService.NegocioVista modulos(@PathVariable Long id, @RequestBody Map<String, String> ajustes) {
        return plataforma.ajustarModulos(id, ajustes);
    }

    @PostMapping("/negocios/{id}/restablecer-acceso")
    PlataformaService.AccesoTemporal restablecerAcceso(@PathVariable Long id) {
        return plataforma.restablecerAdministrador(id);
    }

    @GetMapping("/registro")
    List<PlataformaService.RegistroVista> registro(@RequestParam(required = false) Long negocio) {
        return plataforma.registros(negocio);
    }
}
