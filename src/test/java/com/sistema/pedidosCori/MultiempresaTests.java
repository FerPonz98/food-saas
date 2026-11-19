package com.sistema.pedidosCori;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.sistema.pedidosCori.config.WebSocketConfig;
import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.Pedido;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.service.EjecutorNegocio;
import com.sistema.pedidosCori.service.ModuloService;
import com.sistema.pedidosCori.service.NegocioActual;
import com.sistema.pedidosCori.service.PlataformaService;
import com.sistema.pedidosCori.service.UsuarioSesion;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
class MultiempresaTests {
    @Autowired WebApplicationContext context;
    @Autowired PlataformaService plataforma;
    @Autowired EjecutorNegocio ejecutor;
    @Autowired IPlatoDao platoDao;
    @Autowired IPedidoDao pedidoDao;
    @Autowired IUsuarioDao usuarioDao;
    @Autowired ModuloService modulos;
    @Autowired WebSocketConfig webSocket;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor como(String username, String rol) { return user(username).roles(rol); }

    private record Otro(long id, String slug, String admin) {
        RequestPostProcessor adminB() { return user(admin).roles("ADMIN"); }
    }

    private Otro nuevoNegocio(String plan) {
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        PlataformaService.AccesoTemporal acceso = plataforma.crearNegocio(new PlataformaService.NuevoNegocio(
            "Negocio " + sufijo, "neg-" + sufijo, plan, "ACTIVO", "MENSUAL", null, "admin." + sufijo, "Dueña " + sufijo,
            null, null, null, null));
        assertThat(acceso.passwordTemporal()).isNotBlank();
        return new Otro(acceso.negocio().id(), acceso.negocio().slug(), acceso.username());
    }

    private Plato platoEn(long negocio, String nombre, double precio) {
        return ejecutor.en(negocio, () -> {
            Plato p = new Plato();
            p.setNombre(nombre);
            p.setCategoria("SOPAS");
            p.setPrecio(precio);
            p.setDisponible(true);
            return platoDao.save(p);
        });
    }

    private long pedidoEn(Otro b, long platoId, int mesa) throws Exception {
        String json = mvc().perform(post("/api/pedidos").with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"MESA\",\"numeroMesa\":" + mesa + ",\"detalles\":[{\"platoId\":" + platoId + ",\"cantidad\":2}]}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Test
    void newBusinessStartsWithItsOwnConfigurationCategoriesAndAdmin() {
        Otro b = nuevoNegocio("PRO");
        assertThat(usuarioDao.findByUsername(b.admin()).orElseThrow().getNegocioId()).isEqualTo(b.id());
        assertThat(usuarioDao.findByUsername(b.admin()).orElseThrow().getDebeCambiarPassword()).isTrue();
        NegocioActual.ejecutarComo(b.id(), () -> {
            assertThat(modulos.actual().orElseThrow().planCodigo()).isEqualTo("PRO");
            assertThat(modulos.tiene(com.sistema.pedidosCori.models.entity.Modulo.CARTA_QR)).isTrue();
            assertThat(modulos.tiene(com.sistema.pedidosCori.models.entity.Modulo.AUDITORIA)).isFalse();
        });
        assertThatThrownBy(() -> plataforma.crearNegocio(new PlataformaService.NuevoNegocio("Otro", b.slug(), "PRO", "ACTIVO",
            "MENSUAL", null, "otro.admin." + b.id(), null, null, null, null, null))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void businessesCannotSeeOrChangeEachOthersData() throws Exception {
        Otro b = nuevoNegocio("PREMIUM");
        Plato deB = platoEn(b.id(), "Secreto de B", 30.0);
        long pedidoB = pedidoEn(b, deB.getId(), 2);
        RequestPostProcessor adminA = como("admin", "ADMIN");

        mvc().perform(get("/api/platos/todos").with(adminA)).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].nombre", not(hasItem("Secreto de B"))));
        mvc().perform(get("/api/platos/todos").with(b.adminB())).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].nombre", hasItem("Secreto de B")));
        mvc().perform(get("/api/pedidos/" + pedidoB).with(adminA)).andExpect(status().isNotFound());
        mvc().perform(put("/api/pedidos/" + pedidoB + "/pagar").param("metodoPago", "EFECTIVO").with(adminA).with(csrf()))
            .andExpect(status().isNotFound());
        mvc().perform(put("/api/platos/" + deB.getId()).with(adminA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Robado\",\"precio\":1,\"categoria\":\"SOPAS\"}"))
            .andExpect(status().isNotFound());
        mvc().perform(get("/api/pedidos/activos").with(adminA)).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", not(hasItem((int) pedidoB))));
        mvc().perform(post("/api/pedidos").with(adminA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"MESA\",\"numeroMesa\":1,\"detalles\":[{\"platoId\":" + deB.getId() + ",\"cantidad\":1}]}"))
            .andExpect(status().isNotFound());

        long adminBId = usuarioDao.findByUsername(b.admin()).orElseThrow().getId();
        mvc().perform(get("/api/admin/usuarios").with(adminA)).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].username", not(hasItem(b.admin()))));
        mvc().perform(put("/api/admin/usuarios/" + adminBId).with(adminA).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreCompleto\":\"X\",\"rol\":\"COCINA\",\"activo\":false}"))
            .andExpect(status().isNotFound());
        mvc().perform(get("/api/admin/auditoria").with(adminA)).andExpect(status().isOk())
            .andExpect(jsonPath("$.contenido[*].detalle", not(hasItem(org.hamcrest.Matchers.containsString("Secreto de B")))));

        ejecutor.en(b.id(), () -> assertThat(pedidoDao.findById(pedidoB)).isPresent());
        ejecutor.en(NegocioPrincipalExtension.PRINCIPAL, () -> assertThat(pedidoDao.findById(pedidoB)).isEmpty());
    }

    @Test
    void onlySuperadminManagesThePlatform() throws Exception {
        mvc().perform(get("/api/superadmin/negocios").with(como("admin", "ADMIN"))).andExpect(status().isForbidden());
        mvc().perform(get("/superadmin").with(como("admin", "ADMIN"))).andExpect(redirectedUrl("/?denegado"));
        mvc().perform(get("/api/superadmin/negocios").with(como("superadmin", "SUPERADMIN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$[*].slug", hasItem("principal")));
        mvc().perform(get("/superadmin").with(como("superadmin", "SUPERADMIN"))).andExpect(status().isOk());
        mvc().perform(get("/superadmin/planes").with(como("superadmin", "SUPERADMIN"))).andExpect(status().isOk());
        mvc().perform(get("/admin").with(como("superadmin", "SUPERADMIN"))).andExpect(redirectedUrl("/?denegado"));
        mvc().perform(get("/").with(como("superadmin", "SUPERADMIN"))).andExpect(redirectedUrl("/superadmin"));
        mvc().perform(post("/api/admin/usuarios").with(como("admin", "ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"intruso." + UUID.randomUUID().toString().substring(0, 5) + "\",\"nombreCompleto\":\"X\",\"rol\":\"SUPERADMIN\",\"password\":\"Segura-2026x\"}"))
            .andExpect(status().isBadRequest());

        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        mvc().perform(post("/api/superadmin/negocios").with(como("superadmin", "SUPERADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Café " + sufijo + "\",\"planCodigo\":\"NORMAL\",\"estado\":\"PRUEBA\",\"adminUsername\":\"admin.cafe" + sufijo + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.negocio.slug").value("cafe-" + sufijo))
            .andExpect(jsonPath("$.negocio.estado").value("PRUEBA"))
            .andExpect(jsonPath("$.negocio.venceEn").value(LocalDate.now().plusDays(PlataformaService.DIAS_PRUEBA).toString()))
            .andExpect(jsonPath("$.passwordTemporal").isNotEmpty());
    }

    @Test
    void modulesFollowThePlanAndCanBeAdjustedPerBusiness() throws Exception {
        Otro b = nuevoNegocio("NORMAL");
        mvc().perform(get("/api/admin/auditoria").with(b.adminB())).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("MODULO_NO_INCLUIDO")).andExpect(jsonPath("$.modulo").value("AUDITORIA"));
        mvc().perform(get("/admin/carta-qr").with(b.adminB())).andExpect(redirectedUrl("/admin/plan?modulo=CARTA_QR"));
        mvc().perform(get("/admin/plan").with(b.adminB())).andExpect(status().isOk());
        mvc().perform(get("/api/admin/plan").with(b.adminB())).andExpect(status().isOk())
            .andExpect(jsonPath("$.planCodigo").value("NORMAL"))
            .andExpect(jsonPath("$.modulos[?(@.codigo == 'CARTA_QR')].incluido").value(hasItem(false)));
        mvc().perform(get("/n/" + b.slug() + "/carta")).andExpect(status().isNotFound());
        mvc().perform(get("/api/publico/" + b.slug() + "/carta")).andExpect(status().isNotFound());
        mvc().perform(get("/api/admin/reportes").param("desde", "2025-01-01").param("hasta", "2025-01-31").with(b.adminB()))
            .andExpect(status().isForbidden());
        mvc().perform(get("/api/admin/reportes").with(b.adminB())).andExpect(status().isOk())
            .andExpect(jsonPath("$.rango.desde").value(LocalDate.now().toString()))
            .andExpect(jsonPath("$.anterior").value(org.hamcrest.Matchers.nullValue()));
        mvc().perform(get("/api/admin/reportes/csv").param("tipo", "pedidos").with(b.adminB())).andExpect(status().isForbidden());
        mvc().perform(get("/api/pedidos/1/cuenta").with(b.adminB())).andExpect(status().isForbidden());

        plataforma.ajustarModulos(b.id(), Map.of("CARTA_QR", "ACTIVAR"));
        mvc().perform(get("/n/" + b.slug() + "/carta")).andExpect(status().isOk());
        mvc().perform(get("/api/publico/" + b.slug() + "/carta")).andExpect(status().isOk());

        plataforma.cambiarSuscripcion(b.id(), new PlataformaService.Suscripcion("PREMIUM", "ACTIVO", "ANUAL", null, null));
        mvc().perform(get("/api/admin/auditoria").with(b.adminB())).andExpect(status().isOk());
    }

    @Test
    void appearanceCustomizationRequiresProButNameLogoAndCurrencyDoNot() throws Exception {
        Otro b = nuevoNegocio("NORMAL");
        String base = "\"eslogan\":\"\",\"colorPrimario\":\"#24324A\",\"colorSecundario\":\"#64748B\",\"colorAcento\":\"#C56A3D\","
            + "\"puntoAtencionSingular\":\"Mesa\",\"puntoAtencionPlural\":\"Mesas\",\"etiquetaParaLlevar\":\"Para llevar\","
            + "\"etiquetaRolMesero\":\"Mesero\",\"etiquetaRolCocina\":\"Cocina\",\"cantidadPuntosAtencion\":12";
        mvc().perform(put("/api/admin/configuracion").with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreNegocio\":\"Nuevo nombre\",\"simboloMoneda\":\"$\"," + base + "}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nombreNegocio").value("Nuevo nombre"))
            .andExpect(jsonPath("$.simboloMoneda").value("$"));
        mvc().perform(put("/api/admin/configuracion").with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreNegocio\":\"Nuevo nombre\",\"simboloMoneda\":\"$\"," + base.replace("#24324A", "#112233") + "}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.modulo").value("APARIENCIA_AVANZADA"));
        mvc().perform(get("/api/admin/configuracion").with(como("admin", "ADMIN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$.nombreNegocio").value(not("Nuevo nombre")));
    }

    @Test
    void planLimitsAreSuggestionsThatWarnButNeverBlock() throws Exception {
        Otro b = nuevoNegocio("NORMAL");
        String config = "{\"nombreNegocio\":\"Grande\",\"eslogan\":\"\",\"colorPrimario\":\"#24324A\",\"colorSecundario\":\"#64748B\","
            + "\"colorAcento\":\"#C56A3D\",\"puntoAtencionSingular\":\"Mesa\",\"puntoAtencionPlural\":\"Mesas\","
            + "\"etiquetaParaLlevar\":\"Para llevar\",\"etiquetaRolMesero\":\"Mesero\",\"etiquetaRolCocina\":\"Cocina\","
            + "\"simboloMoneda\":\"Bs.\",\"cantidadPuntosAtencion\":25}";
        mvc().perform(put("/api/admin/configuracion").with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(config))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cantidadPuntosAtencion").value(25));
        for (int i = 0; i < 52; i++) platoEn(b.id(), "Plato " + i, 5.0);
        mvc().perform(post("/api/platos").with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Uno más\",\"precio\":5,\"categoria\":\"SOPAS\"}"))
            .andExpect(status().isOk());
        assertThat(plataforma.negocio(b.id()).avisos())
            .anyMatch(a -> a.contains("25 mesas")).anyMatch(a -> a.contains("53 productos"));
        mvc().perform(post("/api/pedidos").with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"MESA\",\"numeroMesa\":24,\"detalles\":[{\"platoId\":" + platoEn(b.id(), "Último", 3.0).getId() + ",\"cantidad\":1}]}"))
            .andExpect(status().isOk());
    }

    @Test
    void secondAdministratorRequiresPremium() throws Exception {
        Otro b = nuevoNegocio("PRO");
        String cuerpo = "{\"username\":\"segundo." + b.id() + "\",\"nombreCompleto\":\"Socia\",\"rol\":\"ADMIN\",\"password\":\"Segura-2026x\"}";
        mvc().perform(post("/api/admin/usuarios").with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.modulo").value("VARIOS_ADMINISTRADORES"));
        mvc().perform(post("/api/admin/usuarios").with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.replace("ADMIN", "MESERO")))
            .andExpect(status().isCreated());
    }

    @Test
    void suspendedBusinessLosesAccessButKeepsItsData() throws Exception {
        Otro b = nuevoNegocio("PRO");
        Plato p = platoEn(b.id(), "Conservado", 9.0);
        plataforma.cambiarSuscripcion(b.id(), new PlataformaService.Suscripcion("PRO", "SUSPENDIDO", "MENSUAL", null, null));
        mvc().perform(get("/api/platos").with(b.adminB())).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("NEGOCIO_SUSPENDIDO"));
        mvc().perform(get("/admin").with(b.adminB())).andExpect(redirectedUrl("/suspendido"));
        mvc().perform(get("/suspendido").with(b.adminB())).andExpect(status().isOk());
        mvc().perform(get("/n/" + b.slug() + "/carta")).andExpect(status().isNotFound());
        mvc().perform(post("/login").with(csrf()).param("username", b.admin()).param("password", "cualquiera"))
            .andExpect(redirectedUrl("/login?suspendido"));
        ejecutor.en(b.id(), () -> assertThat(platoDao.findById(p.getId())).isPresent());

        plataforma.cambiarSuscripcion(b.id(), new PlataformaService.Suscripcion("PRO", "ACTIVO", "MENSUAL", null, null));
        mvc().perform(get("/api/platos").with(b.adminB())).andExpect(status().isOk());
    }

    @Test
    void customerOrdersFromTheMenuNeedTheWaitersApproval() throws Exception {
        Otro b = nuevoNegocio("PRO");
        Plato sopa = platoEn(b.id(), "Sopa QR", 12.0);
        Plato agotado = platoEn(b.id(), "No hoy", 5.0);
        ejecutor.en(b.id(), () -> { agotado.setDisponible(false); platoDao.save(agotado); });
        String api = "/api/publico/" + b.slug() + "/mesa/3/pedidos";

        mvc().perform(post(api).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"platoId\":" + agotado.getId() + ",\"cantidad\":1}]}"))
            .andExpect(status().isBadRequest());
        mvc().perform(post("/api/publico/principal/mesa/3/pedidos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"platoId\":" + sopa.getId() + ",\"cantidad\":1}]}"))
            .andExpect(status().isBadRequest());

        String json = mvc().perform(post(api).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreCliente\":\"Lucía\",\"items\":[{\"platoId\":" + sopa.getId() + ",\"cantidad\":2,\"notas\":\"sin sal\"}]}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"))
            .andExpect(jsonPath("$.total").value(24.0)).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(json, "$.token");
        mvc().perform(get(api + "/" + token)).andExpect(jsonPath("$.estado").value("PENDIENTE"));
        mvc().perform(get("/api/publico/" + b.slug() + "/mesa/4/pedidos/" + token)).andExpect(status().isNotFound());

        RequestPostProcessor meseroB = como(b.admin(), "ADMIN");
        String pendientes = mvc().perform(get("/api/solicitudes").with(meseroB)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1)).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(pendientes, "$[0].id");
        mvc().perform(get("/api/solicitudes").with(como("admin", "ADMIN"))).andExpect(jsonPath("$[*].id", not(hasItem(id))));
        mvc().perform(put("/api/solicitudes/" + id + "/aprobar").with(como("admin", "ADMIN")).with(csrf())).andExpect(status().isNotFound());

        mvc().perform(put("/api/solicitudes/" + id + "/aprobar").with(meseroB).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.numeroMesa").value(3))
            .andExpect(jsonPath("$.nombreCliente").value("Lucía")).andExpect(jsonPath("$.total").value(24.0))
            .andExpect(jsonPath("$.detalles[0].notas").value("sin sal"));
        mvc().perform(get(api + "/" + token)).andExpect(jsonPath("$.estado").value("APROBADA"));
        mvc().perform(put("/api/solicitudes/" + id + "/aprobar").with(meseroB).with(csrf())).andExpect(status().isConflict());

        String otro = mvc().perform(post(api).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"platoId\":" + sopa.getId() + ",\"cantidad\":1}]}"))
            .andReturn().getResponse().getContentAsString();
        String token2 = JsonPath.read(otro, "$.token");
        Number id2 = JsonPath.read(mvc().perform(get("/api/solicitudes").with(meseroB)).andReturn().getResponse().getContentAsString(), "$[0].id");
        mvc().perform(put("/api/solicitudes/" + id2 + "/aprobar").with(meseroB).with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.detalles.length()").value(2));
        String tercero = mvc().perform(post(api).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"platoId\":" + sopa.getId() + ",\"cantidad\":1}]}"))
            .andReturn().getResponse().getContentAsString();
        Number id3 = JsonPath.read(mvc().perform(get("/api/solicitudes").with(meseroB)).andReturn().getResponse().getContentAsString(), "$[0].id");
        mvc().perform(put("/api/solicitudes/" + id3 + "/rechazar").with(meseroB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Se acabó la sopa\"}"))
            .andExpect(status().isOk());
        mvc().perform(get(api + "/" + JsonPath.read(tercero, "$.token"))).andExpect(jsonPath("$.estado").value("RECHAZADA"))
            .andExpect(jsonPath("$.motivoRechazo").value("Se acabó la sopa"));
        assertThat(token2).isNotEqualTo(token);
    }

    @Test
    void billCanBeSplitByProductsEqualPartsAndAmount() throws Exception {
        Otro b = nuevoNegocio("PRO");
        Plato sopa = platoEn(b.id(), "Sopa dividida", 10.0);
        Plato pollo = platoEn(b.id(), "Pollo dividido", 25.0);
        String json = mvc().perform(post("/api/pedidos").with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"MESA\",\"numeroMesa\":5,\"detalles\":[{\"platoId\":" + sopa.getId() + ",\"cantidad\":2},"
                    + "{\"platoId\":" + pollo.getId() + ",\"cantidad\":1}]}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long pedido = ((Number) JsonPath.read(json, "$.id")).longValue();
        long lineaSopa = ((Number) JsonPath.read(json, "$.detalles[0].id")).longValue();
        String pagos = "/api/pedidos/" + pedido + "/pagos";

        mvc().perform(get("/api/pedidos/" + pedido + "/cuenta").with(b.adminB())).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(45.0)).andExpect(jsonPath("$.saldo").value(45.0));
        mvc().perform(post(pagos).with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"PRODUCTOS\",\"metodoPago\":\"EFECTIVO\",\"persona\":\"Ana\",\"items\":[{\"detalleId\":" + lineaSopa + ",\"cantidad\":1}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.abonado").value(10.0)).andExpect(jsonPath("$.saldo").value(35.0))
            .andExpect(jsonPath("$.lineas[0].cantidadPagada").value(1));
        mvc().perform(post(pagos).with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"PRODUCTOS\",\"metodoPago\":\"EFECTIVO\",\"items\":[{\"detalleId\":" + lineaSopa + ",\"cantidad\":2}]}"))
            .andExpect(status().isConflict());
        mvc().perform(put("/api/pedidos/" + pedido + "/pagar").param("metodoPago", "EFECTIVO").with(b.adminB()).with(csrf()))
            .andExpect(status().isConflict());
        mvc().perform(post(pagos).with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"PARTES\",\"metodoPago\":\"QR\",\"partes\":3}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.saldo").value(20.0));
        mvc().perform(post(pagos).with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"MONTO\",\"metodoPago\":\"TARJETA\",\"monto\":25}"))
            .andExpect(status().isBadRequest());
        mvc().perform(post(pagos).with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"MONTO\",\"metodoPago\":\"TARJETA\",\"monto\":20}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.pagado").value(true)).andExpect(jsonPath("$.saldo").value(0.0))
            .andExpect(jsonPath("$.pagos.length()").value(3));
        Pedido pagado = ejecutor.en(b.id(), () -> pedidoDao.findById(pedido).orElseThrow());
        assertThat(pagado.getMetodoPago()).isEqualTo("DIVIDIDO");
        assertThat(pagado.getFechaPago()).isNotNull();

        String hoy = LocalDate.now().toString();
        String reporte = mvc().perform(get("/api/admin/reportes").param("desde", hoy).param("hasta", hoy).with(b.adminB()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<String> metodos = JsonPath.read(reporte, "$.metodosPago[*].metodo");
        assertThat(metodos).contains("EFECTIVO", "QR", "TARJETA").doesNotContain("DIVIDIDO");
        mvc().perform(post(pagos).with(b.adminB()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"modo\":\"MONTO\",\"metodoPago\":\"EFECTIVO\",\"monto\":1}"))
            .andExpect(status().isConflict());
    }

    @Test
    void waiterConfirmsCallsSoTheCustomerKnowsSomeoneIsComing() throws Exception {
        Otro b = nuevoNegocio("PRO");
        String api = "/api/publico/" + b.slug() + "/mesa/2/llamados";
        mvc().perform(post(api).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"MESERO\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.confirmado").value(false));
        String lista = mvc().perform(get("/api/llamados").with(b.adminB())).andExpect(jsonPath("$.length()").value(1))
            .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(lista, "$[0].id");
        mvc().perform(get("/api/llamados").with(como("admin", "ADMIN"))).andExpect(jsonPath("$[*].id", not(hasItem(id))));
        mvc().perform(put("/api/llamados/" + id + "/confirmar").with(como("admin", "ADMIN")).with(csrf())).andExpect(status().isNotFound());
        mvc().perform(put("/api/llamados/" + id + "/confirmar").with(b.adminB()).with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.confirmadoPor").value(b.admin()));
        mvc().perform(get(api)).andExpect(jsonPath("$[0].confirmado").value(true));
        mvc().perform(put("/api/llamados/" + id + "/atender").with(b.adminB()).with(csrf())).andExpect(status().isOk());
        mvc().perform(get(api)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void pagesShowOnlyWhatThePlanIncludes() throws Exception {
        Otro normal = nuevoNegocio("NORMAL");
        Otro pro = nuevoNegocio("PRO");
        plataforma.ajustarModulos(normal.id(), Map.of("CARTA_QR", "ACTIVAR"));

        mvc().perform(get("/n/" + pro.slug() + "/mesa/1")).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("id=\"btn-carrito\""),
                    org.hamcrest.Matchers.containsString("id=\"btn-llamar\""),
                    org.hamcrest.Matchers.containsString("Carta digital con Gestión Comercial"),
                    org.hamcrest.Matchers.containsString("/n/" + pro.slug() + "/manifest.webmanifest"))));
        mvc().perform(get("/n/" + normal.slug() + "/mesa/1")).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.allOf(not(org.hamcrest.Matchers.containsString("id=\"btn-carrito\"")),
                    not(org.hamcrest.Matchers.containsString("id=\"btn-llamar\"")),
                    not(org.hamcrest.Matchers.containsString("rel=\"manifest\"")))));
        mvc().perform(get("/n/principal/mesa/1")).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                not(org.hamcrest.Matchers.containsString("Carta digital con Gestión Comercial"))));

        mvc().perform(get("/mesero").with(pro.adminB())).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("/js/division-cuenta.js")));
        mvc().perform(get("/mesero").with(normal.adminB())).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                not(org.hamcrest.Matchers.containsString("/js/division-cuenta.js"))));
        mvc().perform(get("/admin").with(normal.adminB())).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("/admin/plan?modulo=AUDITORIA"),
                    org.hamcrest.Matchers.containsString("Mi plan"))));
        mvc().perform(get("/admin/reportes").with(normal.adminB())).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("reporte de ventas del día")));
        mvc().perform(get("/admin/apariencia").with(normal.adminB())).andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                org.hamcrest.Matchers.containsString("disabled=\"disabled\"")));
    }

    @Test
    void realtimeTopicsAreSeparatedByBusiness() {
        UsernamePasswordAuthenticationToken usuario = new UsernamePasswordAuthenticationToken(
            new UsuarioSesion("mesero.x", "x", true, false, List.of(), 7L), null, List.of());
        Message<?> sub = webSocket.temaDeSuscripcion(mensaje(SimpMessageType.SUBSCRIBE, "/topic/cocina", usuario));
        assertThat(SimpMessageHeaderAccessor.getDestination(sub.getHeaders())).isEqualTo("/topic/n7/cocina");
        assertThat(webSocket.temaDeSuscripcion(mensaje(SimpMessageType.SUBSCRIBE, "/topic/n8/cocina", usuario))).isNull();
        assertThat(webSocket.temaDeSuscripcion(mensaje(SimpMessageType.MESSAGE, "/topic/cocina", usuario))).isNull();
        assertThat(webSocket.temaDeSuscripcion(mensaje(SimpMessageType.SUBSCRIBE, "/topic/cocina", null))).isNull();

        Message<?> envio = NegocioActual.ejecutarComo(9L, () -> webSocket.temaDeEnvio(mensaje(SimpMessageType.MESSAGE, "/topic/mesero", null)));
        assertThat(SimpMessageHeaderAccessor.getDestination(envio.getHeaders())).isEqualTo("/topic/n9/mesero");
        assertThat(NegocioActual.ejecutarComo(NegocioActual.NINGUNO,
            () -> webSocket.temaDeEnvio(mensaje(SimpMessageType.MESSAGE, "/topic/mesero", null)))).isNull();
    }

    private static Message<byte[]> mensaje(SimpMessageType tipo, String destino, java.security.Principal usuario) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(tipo);
        accessor.setDestination(destino);
        accessor.setSessionId("s1");
        accessor.setSubscriptionId("sub-1");
        if (usuario != null) accessor.setUser(usuario);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
