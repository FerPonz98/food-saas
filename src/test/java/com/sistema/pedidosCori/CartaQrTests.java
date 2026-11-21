package com.sistema.pedidosCori;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.sistema.pedidosCori.models.dao.ILlamadoMesaDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.service.CategoriaService;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;
import com.sistema.pedidosCori.service.LlamadoService;
import com.sistema.pedidosCori.service.QrService;

@SpringBootTest
class CartaQrTests {
    @Autowired WebApplicationContext context;
    @Autowired IPlatoDao platoDao;
    @Autowired ILlamadoMesaDao llamadoDao;
    @Autowired ConfiguracionNegocioService configuracion;
    @Autowired LlamadoService llamados;
    @Autowired CategoriaService categorias;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor admin() { return user("admin").roles("ADMIN"); }
    private static RequestPostProcessor mesero() { return user("mesero").roles("MESERO"); }
    private static RequestPostProcessor cocina() { return user("cocina").roles("COCINA"); }

    @BeforeEach
    void limpiar() {
        llamadoDao.deleteAll();
    }

    @AfterEach
    void restaurar() {
        configuracion.actualizarWifi(new ConfiguracionNegocioService.Wifi("", ""));
        llamadoDao.deleteAll();
    }

    private Plato plato(String nombre, boolean disponible, boolean archivado) {
        Plato p = new Plato();
        p.setNombre(nombre); p.setCategoria(categorias.listar().get(0).codigo()); p.setPrecio(25.0);
        p.setDisponible(disponible); p.setArchivado(archivado);
        return platoDao.save(p);
    }

    private static String llamado(String motivo, String pago) {
        return "{\"motivo\":\"" + motivo + "\"" + (pago == null ? "" : ",\"metodoPago\":\"" + pago + "\"") + "}";
    }

    @Test
    void publicPagesOpenWithoutLogin() throws Exception {
        mvc().perform(get("/n/principal/carta")).andExpect(status().isOk())
            .andExpect(content().string(containsString("/js/carta.js")))
            .andExpect(content().string(not(containsString("Pedir la cuenta"))));
        mvc().perform(get("/n/principal/mesa/3")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Pedir la cuenta")))
            .andExpect(content().string(containsString("data-mesa=\"3\"")));
    }

    @Test
    void legacyAddressesRedirectToTheBusinessMenu() throws Exception {
        mvc().perform(get("/carta")).andExpect(redirectedUrl("/n/principal/carta"));
        mvc().perform(get("/mesa/3")).andExpect(redirectedUrl("/n/principal/mesa/3"));
        mvc().perform(get("/n/no-existe/carta")).andExpect(status().isNotFound());
        mvc().perform(get("/api/publico/no-existe/carta")).andExpect(status().isNotFound());
    }

    @Test
    void unknownTableRedirectsToGeneralMenu() throws Exception {
        mvc().perform(get("/n/principal/mesa/999")).andExpect(redirectedUrl("/n/principal/carta"));
        mvc().perform(get("/n/principal/mesa/0")).andExpect(redirectedUrl("/n/principal/carta"));
        mvc().perform(get("/n/principal/mesa/abc")).andExpect(redirectedUrl("/n/principal/carta"));
    }

    @Test
    void publicMenuOnlyShowsTodaysProducts() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        plato("Hoy-" + sufijo, true, false);
        plato("NoHoy-" + sufijo, false, false);
        plato("Archivado-" + sufijo, true, true);
        mvc().perform(get("/api/publico/principal/carta")).andExpect(status().isOk())
            .andExpect(jsonPath("$.platos[*].nombre", hasItem("Hoy-" + sufijo)))
            .andExpect(jsonPath("$.platos[*].nombre", not(hasItem("NoHoy-" + sufijo))))
            .andExpect(jsonPath("$.platos[*].nombre", not(hasItem("Archivado-" + sufijo))))
            .andExpect(jsonPath("$.categorias").isArray());
    }

    @Test
    void privateApisStayClosedToCustomers() throws Exception {
        mvc().perform(get("/api/platos")).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/pedidos/activos")).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/llamados")).andExpect(status().isUnauthorized());
        mvc().perform(get("/api/admin/configuracion/wifi")).andExpect(status().isUnauthorized());
    }

    @Test
    void productPhotoIsPublicOnlyWhileOnSale() throws Exception {
        Plato p = plato("SinFoto-" + UUID.randomUUID().toString().substring(0, 6), true, false);
        mvc().perform(get("/api/publico/principal/platos/" + p.getId() + "/imagen")).andExpect(status().isNotFound());
    }

    @Test
    void customerCallReachesWaiterAndIsNotDuplicated() throws Exception {
        mvc().perform(post("/api/publico/principal/mesa/4/llamados").contentType(MediaType.APPLICATION_JSON).content(llamado("MESERO", null)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.motivo").value("MESERO"));
        mvc().perform(post("/api/publico/principal/mesa/4/llamados").contentType(MediaType.APPLICATION_JSON).content(llamado("MESERO", null)))
            .andExpect(status().isOk());
        mvc().perform(post("/api/publico/principal/mesa/4/llamados").contentType(MediaType.APPLICATION_JSON).content(llamado("CUENTA", "QR")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.metodoPago").value("QR"));

        assertThat(llamadoDao.count()).isEqualTo(2);
        String respuesta = mvc().perform(get("/api/llamados").with(mesero())).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(respuesta, "$[0].id");

        mvc().perform(get("/api/publico/principal/mesa/4/llamados")).andExpect(jsonPath("$.length()").value(2));
        mvc().perform(put("/api/llamados/" + id + "/atender").with(mesero()).with(csrf())).andExpect(status().isOk());
        mvc().perform(get("/api/publico/principal/mesa/4/llamados")).andExpect(jsonPath("$.length()").value(1));
        mvc().perform(get("/api/llamados").with(mesero())).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void customerCallGoesThroughPendingAcceptedAndAttended() throws Exception {
        String api = "/api/publico/principal/mesa/6/llamados";
        mvc().perform(post(api).contentType(MediaType.APPLICATION_JSON).content(llamado("MESERO", null)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.confirmado").value(false))
            .andExpect(jsonPath("$.esperaSegundos").isNumber());

        var guardado = llamadoDao.findAll().get(0);
        guardado.setCreadoEn(guardado.getCreadoEn().minusSeconds(95));
        llamadoDao.save(guardado);
        mvc().perform(get(api)).andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].motivo").value("MESERO"))
            .andExpect(jsonPath("$[0].confirmado").value(false))
            .andExpect(jsonPath("$[0].esperaSegundos").value(org.hamcrest.Matchers.greaterThanOrEqualTo(95)));

        mvc().perform(put("/api/llamados/" + guardado.getId() + "/confirmar").with(mesero()).with(csrf())).andExpect(status().isOk());
        mvc().perform(get(api)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].confirmado").value(true));

        mvc().perform(post(api).contentType(MediaType.APPLICATION_JSON).content(llamado("MESERO", null)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.confirmado").value(true));
        assertThat(llamadoDao.count()).isEqualTo(1);

        mvc().perform(put("/api/llamados/" + guardado.getId() + "/atender").with(mesero()).with(csrf())).andExpect(status().isOk());
        mvc().perform(get(api)).andExpect(jsonPath("$.length()").value(0));

        mvc().perform(post(api).contentType(MediaType.APPLICATION_JSON).content(llamado("MESERO", null)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.confirmado").value(false));
        assertThat(llamadoDao.count()).isEqualTo(2);
    }

    @Test
    void tablePageHasCallIndicatorSeparatedFromOrderStatus() throws Exception {
        mvc().perform(get("/n/principal/mesa/6")).andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"carta-llamados\"")))
            .andExpect(content().string(containsString("id=\"carta-estado\"")))
            .andExpect(content().string(containsString("data-etiqueta")));
    }

    @Test
    void invalidCallsAreRejected() throws Exception {
        mvc().perform(post("/api/publico/principal/mesa/4/llamados").contentType(MediaType.APPLICATION_JSON).content(llamado("PIZZA", null)))
            .andExpect(status().isBadRequest());
        mvc().perform(post("/api/publico/principal/mesa/4/llamados").contentType(MediaType.APPLICATION_JSON).content(llamado("CUENTA", "BITCOIN")))
            .andExpect(status().isBadRequest());
        mvc().perform(post("/api/publico/principal/mesa/999/llamados").contentType(MediaType.APPLICATION_JSON).content(llamado("MESERO", null)))
            .andExpect(status().isNotFound());
        assertThat(llamadoDao.count()).isZero();
    }

    @Test
    void kitchenCannotSeeOrAttendCalls() throws Exception {
        mvc().perform(get("/api/llamados").with(cocina())).andExpect(status().isForbidden());
    }

    @Test
    void tooManyCallsFromSameDeviceAreLimited() {
        String ip = "203.0.113." + (int) (Math.random() * 200);
        for (int mesa = 1; mesa <= 8; mesa++) llamados.llamar(mesa, "MESERO", null, ip);
        assertThatThrownBy(() -> llamados.llamar(9, "MESERO", null, ip)).isInstanceOf(LlamadoService.LimiteExcedido.class);
    }

    @Test
    void wifiIsManagedByAdminAndShownOnTablePagesOnly() throws Exception {
        mvc().perform(get("/api/publico/principal/mesa/2/wifi")).andExpect(status().isNotFound());
        mvc().perform(put("/api/admin/configuracion/wifi").with(mesero()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Red\",\"clave\":\"clave1234\"}"))
            .andExpect(status().isForbidden());
        mvc().perform(put("/api/admin/configuracion/wifi").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Red\",\"clave\":\"corta\"}"))
            .andExpect(status().isBadRequest());
        mvc().perform(put("/api/admin/configuracion/wifi").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Local_Clientes\",\"clave\":\"clave1234\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Local_Clientes"));

        mvc().perform(get("/api/publico/principal/mesa/2/wifi")).andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("Local_Clientes")).andExpect(jsonPath("$.clave").value("clave1234"));
        mvc().perform(get("/n/principal/mesa/2")).andExpect(content().string(containsString("id=\"btn-wifi\"")));
        mvc().perform(get("/login")).andExpect(content().string(not(containsString("clave1234"))));
        mvc().perform(get("/n/principal/mesa/2")).andExpect(content().string(not(containsString("clave1234"))));
    }

    @Test
    void adminDownloadsQrCodes() throws Exception {
        mvc().perform(get("/admin/qr/mesa/5.svg").with(admin())).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
            .andExpect(content().string(containsString("<svg")));
        mvc().perform(get("/admin/qr/carta.svg").with(admin())).andExpect(status().isOk());
        mvc().perform(get("/admin/qr/wifi.svg").with(admin())).andExpect(status().isNotFound());
        mvc().perform(get("/admin/qr/mesa/5.svg").with(mesero())).andExpect(redirectedUrl("/?denegado"));
        mvc().perform(get("/admin/carta-qr").with(admin())).andExpect(status().isOk())
            .andExpect(content().string(containsString("Carteles para imprimir")));
    }

    @Test
    void wifiQrTextEscapesSpecialCharacters() {
        assertThat(QrService.contenidoWifi("Mi;Red", "a:b\"c,d\\e"))
            .isEqualTo("WIFI:T:WPA;S:Mi\\;Red;P:a\\:b\\\"c\\,d\\\\e;;");
        assertThat(QrService.contenidoWifi("Abierta", "")).isEqualTo("WIFI:T:nopass;S:Abierta;;");
    }
}
