package com.sistema.pedidosCori;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.sistema.pedidosCori.models.dao.IMovimientoDisponibilidadDao;
import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.dto.PedidoRequestDTO;
import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.models.entity.Rol;
import com.sistema.pedidosCori.models.entity.TipoDisponibilidad;
import com.sistema.pedidosCori.models.entity.Usuario;
import com.sistema.pedidosCori.service.CategoriaService;
import com.sistema.pedidosCori.service.ComandaService;
import com.sistema.pedidosCori.service.DisponibilidadService;
import com.sistema.pedidosCori.service.EjecutorNegocio;
import com.sistema.pedidosCori.service.NegocioActual;
import com.sistema.pedidosCori.service.PlataformaService;
import com.sistema.pedidosCori.service.UsuarioDetailsService;

@SpringBootTest
class DisponibilidadTests {
    @Autowired WebApplicationContext context;
    @Autowired IPlatoDao platoDao;
    @Autowired IPedidoDao pedidoDao;
    @Autowired IUsuarioDao usuarioDao;
    @Autowired IMovimientoDisponibilidadDao movimientos;
    @Autowired CategoriaService categorias;
    @Autowired ComandaService comandas;
    @Autowired PlataformaService plataforma;
    @Autowired EjecutorNegocio ejecutor;
    @Autowired UsuarioDetailsService detalles;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor admin() { return user("admin").authorities(() -> "ROLE_ADMIN", () -> DisponibilidadService.PERMISO); }
    private static RequestPostProcessor mesero() { return user("mesero").roles("MESERO"); }
    private static RequestPostProcessor cocinaSinPermiso() { return user("cocina").roles("COCINA"); }
    private static RequestPostProcessor cocinaConPermiso() {
        return user("cocina").authorities(() -> "ROLE_COCINA", () -> DisponibilidadService.PERMISO);
    }

    private Plato plato(Integer stock, TipoDisponibilidad tipo) {
        Plato p = new Plato();
        p.setNombre("Disp-" + UUID.randomUUID().toString().substring(0, 8));
        p.setCategoria(categorias.listar().get(0).codigo());
        p.setPrecio(10.0);
        p.setDisponible(true);
        p.setStockDisponible(stock);
        p.setTipoDisponibilidad(tipo);
        return platoDao.save(p);
    }

    private static String pedido(long platoId, int cantidad) {
        return "{\"tipo\":\"LLEVAR\",\"nombreCliente\":\"Prueba\",\"detalles\":[{\"platoId\":" + platoId + ",\"cantidad\":" + cantidad + "}]}";
    }

    private long crearPedido(MockMvc mvc, long platoId, int cantidad) throws Exception {
        String json = mvc.perform(post("/api/pedidos").with(mesero()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(pedido(platoId, cantidad))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private Integer stock(long platoId) {
        return platoDao.findById(platoId).orElseThrow().getStockDisponible();
    }

    @Test
    void limitedProductIsDiscountedOnConfirmAndBlockedWhenSoldOut() throws Exception {
        MockMvc mvc = mvc();
        Plato p = plato(2, TipoDisponibilidad.LIMITADA);
        crearPedido(mvc, p.getId(), 2);
        assertThat(stock(p.getId())).isZero();

        mvc.perform(post("/api/pedidos").with(mesero()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(pedido(p.getId(), 1)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("agot")));
        assertThat(stock(p.getId())).isZero();

        mvc.perform(get("/api/disponibilidad").with(admin())).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + p.getId() + ")].estado", hasItem("AGOTADO")));
        mvc.perform(get("/api/publico/principal/carta")).andExpect(status().isOk())
            .andExpect(jsonPath("$.platos[?(@.id == " + p.getId() + ")].stockDisponible", hasItem(0)));
    }

    @Test
    void notEnoughUnitsShowsHowManyAreLeft() throws Exception {
        Plato p = plato(3, TipoDisponibilidad.LIMITADA);
        mvc().perform(post("/api/pedidos").with(mesero()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(pedido(p.getId(), 5)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("Solo quedan 3")));
        assertThat(stock(p.getId())).isEqualTo(3);
    }

    @Test
    void pausedProductCannotBeOrderedUntilReactivated() throws Exception {
        MockMvc mvc = mvc();
        Plato p = plato(null, TipoDisponibilidad.CONTINUA);
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/pausa").with(cocinaConPermiso()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"pausado\":true,\"motivo\":\"Se acabó el gas\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PAUSADO"));
        mvc.perform(post("/api/pedidos").with(mesero()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(pedido(p.getId(), 1)))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/publico/principal/mesa/2/pedidos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"platoId\":" + p.getId() + ",\"cantidad\":1}]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje", containsString("agotado")));
        mvc.perform(get("/api/publico/principal/carta"))
            .andExpect(jsonPath("$.platos[?(@.id == " + p.getId() + ")].pausado", hasItem(true)));

        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/pausa").with(cocinaConPermiso()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"pausado\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DISPONIBLE"));
        crearPedido(mvc, p.getId(), 1);
    }

    @Test
    void manualAndContinuousModesHaveNoCountAndLimitedNeedsOne() throws Exception {
        MockMvc mvc = mvc();
        Plato p = plato(8, TipoDisponibilidad.LIMITADA);
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/modalidad").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"MANUAL\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("MANUAL")).andExpect(jsonPath("$.stock").doesNotExist());
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/pausa").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"pausado\":true}"))
            .andExpect(jsonPath("$.estado").value("AGOTADO"));
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/cantidad").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":1}"))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/modalidad").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"CONTINUA\"}"))
            .andExpect(jsonPath("$.tipo").value("CONTINUA")).andExpect(jsonPath("$.estado").value("DISPONIBLE"));
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/modalidad").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"LIMITADA\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/modalidad").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"LIMITADA\",\"cantidad\":4}"))
            .andExpect(jsonPath("$.stock").value(4));
    }

    @Test
    void kitchenAdjustmentsAreValidatedAndLoggedWithWhoWhenAndWhy() throws Exception {
        MockMvc mvc = mvc();
        Plato p = plato(5, TipoDisponibilidad.LIMITADA);
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/cantidad").with(cocinaConPermiso()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":3,\"motivo\":\"Se preparó otra olla\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(8));
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/cantidad").with(cocinaConPermiso()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"delta\":-20}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/cantidad").with(cocinaConPermiso()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":2,\"motivo\":\"Merma\"}"))
            .andExpect(jsonPath("$.stock").value(2));
        assertThat(stock(p.getId())).isEqualTo(2);

        mvc.perform(get("/api/disponibilidad/movimientos").with(cocinaConPermiso())).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.platoId == " + p.getId() + " && @.motivo == 'Merma')].usuario", hasItem("cocina")))
            .andExpect(jsonPath("$[?(@.platoId == " + p.getId() + " && @.motivo == 'Merma')].cantidadAntes", hasItem(8)))
            .andExpect(jsonPath("$[?(@.platoId == " + p.getId() + " && @.motivo == 'Merma')].cantidadDespues", hasItem(2)))
            .andExpect(jsonPath("$[?(@.platoId == " + p.getId() + " && @.motivo == 'Se preparó otra olla')].tipo", hasItem("AJUSTE")))
            .andExpect(jsonPath("$[?(@.platoId == " + p.getId() + ")].fecha").isNotEmpty());
    }

    @Test
    void cancellingAnOrderRestoresUnitsExactlyOnce() throws Exception {
        MockMvc mvc = mvc();
        Plato p = plato(5, TipoDisponibilidad.LIMITADA);
        long id = crearPedido(mvc, p.getId(), 3);
        assertThat(stock(p.getId())).isEqualTo(2);

        mvc.perform(put("/api/pedidos/" + id + "/anular").with(mesero()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/pedidos/" + id + "/anular").with(mesero()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"El cliente se fue\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ANULADO"))
            .andExpect(jsonPath("$.anuladoPor").value("mesero"));
        assertThat(stock(p.getId())).isEqualTo(5);

        mvc.perform(put("/api/pedidos/" + id + "/anular").with(mesero()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"otra vez\"}"))
            .andExpect(status().isConflict());
        assertThat(stock(p.getId())).isEqualTo(5);

        mvc.perform(put("/api/pedidos/" + id + "/pagar").param("metodoPago", "EFECTIVO").with(mesero()).with(csrf()))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/pedidos/" + id + "/detalles").with(mesero()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"platoId\":" + p.getId() + ",\"cantidad\":1}]"))
            .andExpect(status().isConflict());
        mvc.perform(get("/api/pedidos/activos").with(mesero()))
            .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
        mvc.perform(get("/api/pedidos/cocina").with(cocinaSinPermiso()))
            .andExpect(jsonPath("$[*].id", not(hasItem((int) id))));
        assertThat(movimientos.findAll()).anyMatch(m -> id == (m.getPedidoId() == null ? -1 : m.getPedidoId())
            && m.getCantidadAntes() == 2 && m.getCantidadDespues() == 5);
    }

    @Test
    void cancellingDoesNotRestoreUnitsThatWereNeverCounted() throws Exception {
        MockMvc mvc = mvc();
        Plato p = plato(null, TipoDisponibilidad.CONTINUA);
        long id = crearPedido(mvc, p.getId(), 2);
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/modalidad").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"tipo\":\"LIMITADA\",\"cantidad\":4}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/pedidos/" + id + "/anular").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Error de carga\"}"))
            .andExpect(status().isOk());
        assertThat(stock(p.getId())).isEqualTo(4);
    }

    @Test
    void paidOrdersCannotBeCancelledAndKitchenCannotCancel() throws Exception {
        MockMvc mvc = mvc();
        Plato p = plato(4, TipoDisponibilidad.LIMITADA);
        long id = crearPedido(mvc, p.getId(), 1);
        mvc.perform(put("/api/pedidos/" + id + "/anular").with(cocinaConPermiso()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/" + id + "/pagar").param("metodoPago", "EFECTIVO").with(mesero()).with(csrf()))
            .andExpect(status().isOk());
        mvc.perform(put("/api/pedidos/" + id + "/anular").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}"))
            .andExpect(status().isConflict());
        assertThat(stock(p.getId())).isEqualTo(3);
    }

    @Test
    void qrApprovalChecksStockAndLeavesRequestPendingWhenItFails() throws Exception {
        MockMvc mvc = mvc();
        Plato p = plato(1, TipoDisponibilidad.LIMITADA);
        String r = mvc.perform(post("/api/publico/principal/mesa/3/pedidos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"platoId\":" + p.getId() + ",\"cantidad\":2}]}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(r, "$.token");
        String lista = mvc.perform(get("/api/solicitudes").with(mesero())).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(lista, "$[*].id");
        Number solicitud = ids.get(ids.size() - 1);

        mvc.perform(put("/api/solicitudes/" + solicitud + "/aprobar").with(mesero()).with(csrf()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.mensaje", containsString("Solo quedan 1")));
        assertThat(stock(p.getId())).isEqualTo(1);
        mvc.perform(get("/api/publico/principal/mesa/3/pedidos/" + token)).andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void onlyUsersWithThePermissionCanManageAvailability() throws Exception {
        MockMvc mvc = mvc();
        Plato p = plato(3, TipoDisponibilidad.LIMITADA);
        String body = "{\"delta\":1}";
        mvc.perform(get("/api/disponibilidad")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/disponibilidad").with(cocinaSinPermiso())).andExpect(status().isForbidden());
        mvc.perform(get("/api/disponibilidad").with(mesero())).andExpect(status().isForbidden());
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/cantidad").with(cocinaSinPermiso()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/disponibilidad/platos/" + p.getId() + "/cantidad").with(cocinaConPermiso())
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/disponibilidad").with(cocinaConPermiso())).andExpect(status().isOk());
        assertThat(stock(p.getId())).isEqualTo(3);
    }

    @Test
    void permissionIsAssignableToKitchenAndAlwaysPresentForAdmins() throws Exception {
        MockMvc mvc = mvc();
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        String crear = "{\"username\":\"coc" + sufijo + "\",\"nombreCompleto\":\"Jefa de cocina\",\"rol\":\"COCINA\","
            + "\"password\":\"Cocina-Segura-2026\",\"gestionaDisponibilidad\":true}";
        String json = mvc.perform(post("/api/admin/usuarios").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(crear))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.gestionaDisponibilidad").value(true))
            .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$.id");
        assertThat(permisos("coc" + sufijo)).contains(DisponibilidadService.PERMISO);

        mvc.perform(put("/api/admin/usuarios/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreCompleto\":\"Jefa de cocina\",\"rol\":\"COCINA\",\"gestionaDisponibilidad\":false}"))
            .andExpect(jsonPath("$.gestionaDisponibilidad").value(false));
        assertThat(permisos("coc" + sufijo)).doesNotContain(DisponibilidadService.PERMISO);

        mvc.perform(put("/api/admin/usuarios/" + id).with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreCompleto\":\"Ahora mesera\",\"rol\":\"MESERO\",\"gestionaDisponibilidad\":true}"))
            .andExpect(jsonPath("$.gestionaDisponibilidad").value(false));
        assertThat(permisos("coc" + sufijo)).doesNotContain(DisponibilidadService.PERMISO);
        assertThat(permisos("admin")).contains(DisponibilidadService.PERMISO);
        Usuario u = usuarioDao.findByUsername("coc" + sufijo).orElseThrow();
        assertThat(u.getRol()).isEqualTo(Rol.MESERO);
        assertThat(u.getGestionaDisponibilidad()).isFalse();
    }

    private List<String> permisos(String username) {
        UserDetails d = detalles.loadUserByUsername(username);
        return d.getAuthorities().stream().map(a -> a.getAuthority()).toList();
    }

    @Test
    void planWithoutTheModuleCannotUseTheKitchenPanelButStockStillProtectsOrders() throws Exception {
        MockMvc mvc = mvc();
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        PlataformaService.AccesoTemporal acceso = plataforma.crearNegocio(new PlataformaService.NuevoNegocio(
            "Normal " + sufijo, "nor-" + sufijo, "NORMAL", "ACTIVO", "MENSUAL", null, "adm." + sufijo, "Dueño", null, null, null, null));
        RequestPostProcessor adminNormal = user(acceso.username()).authorities(() -> "ROLE_ADMIN", () -> DisponibilidadService.PERMISO);
        mvc.perform(get("/api/disponibilidad").with(adminNormal)).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("MODULO_NO_INCLUIDO"));
        mvc.perform(get("/admin/disponibilidad").with(user(acceso.username()).roles("ADMIN")))
            .andExpect(status().is3xxRedirection());

        long negocio = acceso.negocio().id();
        Plato ajeno = ejecutor.en(negocio, () -> {
            Plato x = new Plato();
            x.setNombre("Ajeno " + sufijo); x.setCategoria("SOPAS"); x.setPrecio(5.0); x.setDisponible(true);
            x.setStockDisponible(1); x.setTipoDisponibilidad(TipoDisponibilidad.LIMITADA);
            return platoDao.save(x);
        });
        mvc.perform(post("/api/pedidos").with(user(acceso.username()).roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(pedido(ajeno.getId(), 2))).andExpect(status().isConflict());

        mvc.perform(put("/api/disponibilidad/platos/" + ajeno.getId() + "/cantidad").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"cantidad\":50}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/disponibilidad").with(admin()))
            .andExpect(jsonPath("$[*].id", not(hasItem(ajeno.getId().intValue()))));
        assertThat(ejecutor.en(negocio, (java.util.function.Supplier<Integer>) () -> platoDao.findById(ajeno.getId()).orElseThrow().getStockDisponible())).isEqualTo(1);
    }

    @Test
    void simultaneousOrdersNeverOversell() throws Exception {
        Plato p = plato(5, TipoDisponibilidad.LIMITADA);
        int intentos = 12;
        ExecutorService pool = Executors.newFixedThreadPool(intentos);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < intentos; i++) {
            Callable<Boolean> tarea = () -> {
                NegocioActual.establecer(NegocioPrincipalExtension.PRINCIPAL);
                try {
                    largada.await();
                    PedidoRequestDTO dto = new PedidoRequestDTO();
                    dto.setTipo("LLEVAR");
                    dto.setNombreCliente("Concurrente");
                    PedidoRequestDTO.DetalleRequestDTO d = new PedidoRequestDTO.DetalleRequestDTO();
                    d.setPlatoId(p.getId());
                    d.setCantidad(1);
                    dto.setDetalles(List.of(d));
                    comandas.crear(dto);
                    return true;
                } catch (IllegalStateException e) {
                    return false;
                } finally {
                    NegocioActual.limpiar();
                }
            };
            resultados.add(pool.submit(tarea));
        }
        largada.countDown();
        int exitosos = 0;
        for (Future<Boolean> r : resultados) if (r.get(30, TimeUnit.SECONDS)) exitosos++;
        pool.shutdown();
        assertThat(exitosos).isEqualTo(5);
        assertThat(stock(p.getId())).isZero();
    }

    @Test
    void simultaneousCancellationsRestoreStockOnlyOnce() throws Exception {
        Plato p = plato(4, TipoDisponibilidad.LIMITADA);
        long id = crearPedido(mvc(), p.getId(), 3);
        int intentos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(intentos);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < intentos; i++) {
            resultados.add(pool.submit(() -> {
                largada.await();
                return mvc().perform(put("/api/pedidos/" + id + "/anular").with(mesero()).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Duplicado\"}")).andReturn().getResponse().getStatus();
            }));
        }
        largada.countDown();
        int ok = 0;
        for (Future<Integer> r : resultados) if (r.get(30, TimeUnit.SECONDS) == 200) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(stock(p.getId())).isEqualTo(4);
        assertThat(pedidoDao.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoPedido.ANULADO);
    }
}
