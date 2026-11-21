package com.sistema.pedidosCori;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.sistema.pedidosCori.models.dao.IAvisoCobroDao;
import com.sistema.pedidosCori.models.dao.INegocioDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.entity.AvisoCobro;
import com.sistema.pedidosCori.models.entity.EstadoSuscripcion;
import com.sistema.pedidosCori.models.entity.Negocio;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.service.CategoriaService;
import com.sistema.pedidosCori.service.CobranzaService;
import com.sistema.pedidosCori.service.EjecutorNegocio;
import com.sistema.pedidosCori.service.NegocioActual;
import com.sistema.pedidosCori.service.PlataformaService;

@SpringBootTest
class CobranzaTests {
    private static final byte[] GIF = {
        'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, (byte) 0x80, 0, 0, (byte) 0xFF, 0, 0, 0, 0, (byte) 0xFF,
        0x21, (byte) 0xF9, 4, 0, 10, 0, 0, 0,
        0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x44, 1, 0,
        0x21, (byte) 0xF9, 4, 0, 10, 0, 0, 0,
        0x2C, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x4C, 1, 0,
        0x3B
    };

    @Autowired WebApplicationContext context;
    @Autowired PlataformaService plataforma;
    @Autowired CobranzaService cobranza;
    @Autowired INegocioDao negocios;
    @Autowired IAvisoCobroDao avisos;
    @Autowired IPlatoDao platoDao;
    @Autowired CategoriaService categorias;
    @Autowired EjecutorNegocio ejecutor;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private record Nuevo(long id, String admin) {
    }

    private Nuevo negocio(String plan, LocalDate venceEn) {
        String s = UUID.randomUUID().toString().substring(0, 6);
        PlataformaService.AccesoTemporal a = plataforma.crearNegocio(new PlataformaService.NuevoNegocio(
            "Cobro " + s, "cob-" + s, plan, "ACTIVO", "MENSUAL", venceEn, "adm." + s, "Dueña", null, null, null, null));
        return new Nuevo(a.negocio().id(), a.username());
    }

    private Negocio leer(long id) {
        return ejecutor.en(NegocioActual.RAIZ, () -> negocios.findById(id).orElseThrow());
    }

    private List<AvisoCobro.Tipo> tipos(long id) {
        return ejecutor.en(NegocioActual.RAIZ, () -> avisos.findTop20ByNegocioIdOrderByFechaDescIdDesc(id).stream()
            .map(AvisoCobro::getTipo).toList());
    }

    @Test
    void invoiceIncludesPlanAndPaidExtraModulesButNotModulesAlreadyInThePlan() {
        LocalDate hoy = LocalDate.now();
        Nuevo n = negocio("PRO", hoy.plusDays(3));
        plataforma.ajustarModulos(n.id(), Map.of("AUDITORIA", "ACTIVAR", "CARTA_QR", "ACTIVAR"));
        cobranza.actualizarPrecio("AUDITORIA", new CobranzaService.DatosPrecio(new BigDecimal("150"), new BigDecimal("1500"), true));

        CobranzaService.ResultadoCobranza r = cobranza.ejecutarPara(n.id(), hoy);
        assertThat(r.facturasEmitidas()).isEqualTo(1);
        CobranzaService.FacturaVista f = cobranza.estado(n.id()).pendiente();
        assertThat(f.numero()).matches("F-\\d{4}-\\d{6}");
        assertThat(f.lineas()).extracting(CobranzaService.LineaVista::concepto)
            .containsExactly("Plan Pro · mensual", "Módulo extra: Registro de actividad");
        assertThat(f.total()).isEqualByComparingTo("1350.00");
        assertThat(f.periodoDesde()).isEqualTo(hoy.plusDays(3));
        assertThat(f.periodoHasta()).isEqualTo(hoy.plusDays(3).plusMonths(1));
        assertThat(tipos(n.id())).contains(AvisoCobro.Tipo.FACTURA_EMITIDA, AvisoCobro.Tipo.RECORDATORIO);

        CobranzaService.ResultadoCobranza otraVez = cobranza.ejecutarPara(n.id(), hoy);
        assertThat(otraVez.facturasEmitidas()).isZero();
        assertThat(otraVez.avisosEnviados()).isZero();
        assertThatThrownBy(() -> cobranza.emitir(n.id())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unpaidBusinessIsWarnedThenSuspendedAndReactivatedWhenPaid() throws Exception {
        LocalDate vence = LocalDate.now().plusDays(2);
        Nuevo n = negocio("PRO", vence);
        cobranza.ejecutarPara(n.id(), LocalDate.now());
        long factura = cobranza.estado(n.id()).pendiente().id();

        cobranza.ejecutarPara(n.id(), vence.plusDays(2));
        assertThat(tipos(n.id())).contains(AvisoCobro.Tipo.VENCIDA);
        assertThat(leer(n.id()).getEstado()).isEqualTo(EstadoSuscripcion.ACTIVO);

        cobranza.ejecutarPara(n.id(), vence.plusDays(5));
        assertThat(tipos(n.id())).contains(AvisoCobro.Tipo.ULTIMO_AVISO);
        assertThat(leer(n.id()).getEstado()).isEqualTo(EstadoSuscripcion.ACTIVO);

        CobranzaService.ResultadoCobranza r = cobranza.ejecutarPara(n.id(), vence.plusDays(6));
        assertThat(r.negociosSuspendidos()).isEqualTo(1);
        Negocio suspendido = leer(n.id());
        assertThat(suspendido.getEstado()).isEqualTo(EstadoSuscripcion.SUSPENDIDO);
        assertThat(suspendido.getMotivoSuspension()).isEqualTo(CobranzaService.SUSPENSION_POR_PAGO);
        assertThat(tipos(n.id())).contains(AvisoCobro.Tipo.SUSPENDIDO);
        mvc().perform(get("/admin").with(user(n.admin()).roles("ADMIN"))).andExpect(redirectedUrl("/suspendido"));

        CobranzaService.FacturaVista pagada = cobranza.registrarPago(factura, new CobranzaService.Pago("Transferencia", "00123", null));
        assertThat(pagada.estado()).isEqualTo("PAGADA");
        Negocio activo = leer(n.id());
        assertThat(activo.getEstado()).isEqualTo(EstadoSuscripcion.ACTIVO);
        assertThat(activo.getMotivoSuspension()).isNull();
        assertThat(activo.getVenceEn()).isEqualTo(vence.plusMonths(1));
        assertThat(tipos(n.id())).contains(AvisoCobro.Tipo.PAGO_RECIBIDO);
        mvc().perform(get("/admin").with(user(n.admin()).roles("ADMIN"))).andExpect(status().isOk());
        assertThatThrownBy(() -> cobranza.registrarPago(factura, new CobranzaService.Pago("QR", null, null)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void manualSuspensionIsNeverLiftedByAPayment() {
        LocalDate vence = LocalDate.now().plusDays(1);
        Nuevo n = negocio("NORMAL", vence);
        cobranza.ejecutarPara(n.id(), LocalDate.now());
        long factura = cobranza.estado(n.id()).pendiente().id();
        plataforma.cambiarSuscripcion(n.id(), new PlataformaService.Suscripcion("NORMAL", "SUSPENDIDO", "MENSUAL", null, vence));
        assertThat(leer(n.id()).getMotivoSuspension()).isEqualTo(CobranzaService.SUSPENSION_MANUAL);
        assertThat(cobranza.ejecutarPara(n.id(), vence.plusDays(30)).avisosEnviados()).isZero();
        cobranza.registrarPago(factura, new CobranzaService.Pago("Efectivo", null, null));
        assertThat(leer(n.id()).getEstado()).isEqualTo(EstadoSuscripcion.SUSPENDIDO);
    }

    @Test
    void automaticSuspensionCanBeTurnedOff() {
        CobranzaService.Parametros antes = cobranza.parametros();
        try {
            cobranza.actualizarParametros(new CobranzaService.Parametros(7, 7, 2, false, "Pagar por QR"));
            LocalDate vence = LocalDate.now().plusDays(1);
            Nuevo n = negocio("PRO", vence);
            cobranza.ejecutarPara(n.id(), LocalDate.now());
            cobranza.ejecutarPara(n.id(), vence.plusDays(1));
            assertThat(cobranza.ejecutarPara(n.id(), vence.plusDays(20)).negociosSuspendidos()).isZero();
            assertThat(leer(n.id()).getEstado()).isEqualTo(EstadoSuscripcion.ACTIVO);
            assertThat(tipos(n.id())).contains(AvisoCobro.Tipo.VENCIDA).doesNotContain(AvisoCobro.Tipo.SUSPENDIDO);
        } finally {
            cobranza.actualizarParametros(antes);
        }
    }

    @Test
    void cancelledInvoiceStopsWarningsAndAllowsANewOne() {
        Nuevo n = negocio("PRO", LocalDate.now().plusDays(2));
        cobranza.ejecutarPara(n.id(), LocalDate.now());
        long factura = cobranza.estado(n.id()).pendiente().id();
        assertThatThrownBy(() -> cobranza.anular(factura, " ")).isInstanceOf(IllegalArgumentException.class);
        cobranza.anular(factura, "Cambio de plan");
        assertThat(cobranza.estado(n.id()).pendiente()).isNull();
        CobranzaService.FacturaVista nueva = cobranza.emitir(n.id());
        assertThat(nueva.id()).isNotEqualTo(factura);
    }

    @Test
    void simultaneousPaymentsAreRegisteredOnlyOnce() throws Exception {
        Nuevo n = negocio("PRO", LocalDate.now().plusDays(2));
        cobranza.ejecutarPara(n.id(), LocalDate.now());
        long factura = cobranza.estado(n.id()).pendiente().id();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> r = new ArrayList<>();
        for (int i = 0; i < 5; i++) r.add(pool.submit(() -> {
            largada.await();
            try {
                cobranza.registrarPago(factura, new CobranzaService.Pago("QR", null, null));
                return true;
            } catch (IllegalStateException e) {
                return false;
            }
        }));
        largada.countDown();
        int ok = 0;
        for (Future<Boolean> f : r) if (f.get(30, TimeUnit.SECONDS)) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(leer(n.id()).getVenceEn()).isEqualTo(LocalDate.now().plusDays(2).plusMonths(1));
    }

    @Test
    void billingIsOnlyForSuperadminAndEachBusinessSeesItsOwnInvoices() throws Exception {
        Nuevo n = negocio("PRO", LocalDate.now().plusDays(2));
        cobranza.ejecutarPara(n.id(), LocalDate.now());
        MockMvc mvc = mvc();
        mvc.perform(get("/api/superadmin/facturas").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/superadmin/facturas").with(user("superadmin").roles("SUPERADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/facturacion").with(user(n.admin()).roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.pendiente.negocioId").value(n.id()))
            .andExpect(jsonPath("$.instruccionesPago").isNotEmpty());
        mvc.perform(get("/api/admin/facturacion").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.pendiente").doesNotExist());
        mvc.perform(get("/api/admin/facturacion").with(user("mesero").roles("MESERO"))).andExpect(status().isForbidden());
    }

    @Test
    void animatedPhotosAreForPremiumAndFreezeWhenTheModuleIsRemoved() throws Exception {
        MockMvc mvc = mvc();
        Plato p = new Plato();
        p.setNombre("Gif-" + UUID.randomUUID().toString().substring(0, 6));
        p.setCategoria(categorias.listar().get(0).codigo());
        p.setPrecio(10.0);
        p.setDisponible(true);
        Plato plato = platoDao.save(p);
        MockMultipartFile gif = new MockMultipartFile("archivo", "plato.gif", "image/gif", GIF);
        mvc.perform(multipart(HttpMethod.PUT, "/api/admin/platos/" + plato.getId() + "/imagen").file(gif)
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(get("/api/platos/" + plato.getId() + "/imagen").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/gif"));

        byte[] roto = java.util.Arrays.copyOf(GIF, GIF.length - 1);
        mvc.perform(multipart(HttpMethod.PUT, "/api/admin/platos/" + plato.getId() + "/imagen")
                .file(new MockMultipartFile("archivo", "plato.gif", "image/gif", roto))
                .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isBadRequest());

        Nuevo pro = negocio("PRO", null);
        Plato ajeno = ejecutor.en(pro.id(), () -> {
            Plato x = new Plato();
            x.setNombre("Pro plato"); x.setCategoria("SOPAS"); x.setPrecio(5.0); x.setDisponible(true);
            return platoDao.save(x);
        });
        mvc.perform(multipart(HttpMethod.PUT, "/api/admin/platos/" + ajeno.getId() + "/imagen").file(gif)
                .with(user(pro.admin()).roles("ADMIN")).with(csrf()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("MODULO_NO_INCLUIDO"));

        long principal = NegocioPrincipalExtension.PRINCIPAL;
        try {
            plataforma.ajustarModulos(principal, Map.of("FOTOS_ANIMADAS", "DESACTIVAR"));
            mvc.perform(get("/api/platos/" + plato.getId() + "/imagen").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"));
        } finally {
            plataforma.ajustarModulos(principal, Map.of());
        }
    }
}
