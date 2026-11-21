package com.sistema.pedidosCori;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.sistema.pedidosCori.service.ConfiguracionNegocioService;
import com.sistema.pedidosCori.service.MarcaPlataformaService;
import com.sistema.pedidosCori.service.PlataformaService;

@SpringBootTest
class MarcaPlataformaTests {
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==");

    @Autowired WebApplicationContext context;
    @Autowired MarcaPlataformaService marca;
    @Autowired PlataformaService plataforma;
    @Autowired ConfiguracionNegocioService configuracion;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void restaurar() {
        marca.actualizar(new MarcaPlataformaService.DatosMarca("Plataforma", "Administración de negocios y planes", "#24324A", "#64748B", "#C56A3D"));
        marca.quitarLogo();
    }

    @Test
    void superadminPanelUsesThePlatformBrandNotABusinessOne() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(put("/api/superadmin/marca").with(user("superadmin").roles("SUPERADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"FEPZON\",\"eslogan\":\"Panel\",\"colorPrimario\":\"#5b2a86\",\"colorSecundario\":\"#3a3a3a\",\"colorAcento\":\"#ff8800\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.colorPrimario").value("#5B2A86"));
        mvc.perform(multipart(HttpMethod.PUT, "/api/superadmin/marca/logo")
                .file(new MockMultipartFile("archivo", "logo.png", "image/png", PNG))
                .with(user("superadmin").roles("SUPERADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.logoPropio").value(true));

        mvc.perform(get("/superadmin").with(user("superadmin").roles("SUPERADMIN"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("/img/plataforma/logo?v=")))
            .andExpect(content().string(containsString(">FEPZON<")))
            .andExpect(content().string(not(containsString("/n/principal/logo"))))
            .andExpect(content().string(containsString("#5B2A86")));

        mvc.perform(get("/img/plataforma/logo")).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(get("/admin").with(user("admin").roles("ADMIN"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("/n/principal/logo")))
            .andExpect(content().string(not(containsString("/img/plataforma/logo"))));
    }

    @Test
    void superadminCanChangeEachBusinessColorsAndOthersCannot() throws Exception {
        MockMvc mvc = mvc();
        String s = UUID.randomUUID().toString().substring(0, 6);
        PlataformaService.AccesoTemporal a = plataforma.crearNegocio(new PlataformaService.NuevoNegocio(
            "Color " + s, "col-" + s, "NORMAL", "ACTIVO", "MENSUAL", null, "adm." + s, "Dueño", null, null, null, null));
        long id = a.negocio().id();
        String colores = "{\"colorPrimario\":\"#112233\",\"colorSecundario\":\"#445566\",\"colorAcento\":\"#AA5500\"}";
        mvc.perform(put("/api/superadmin/negocios/" + id + "/colores").with(user("admin").roles("ADMIN")).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(colores)).andExpect(status().isForbidden());
        mvc.perform(put("/api/superadmin/negocios/" + id + "/colores").with(user("superadmin").roles("SUPERADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"colorPrimario\":\"rojo\",\"colorSecundario\":\"#445566\",\"colorAcento\":\"#AA5500\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/superadmin/negocios/" + id + "/colores").with(user("superadmin").roles("SUPERADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(colores))
            .andExpect(status().isOk()).andExpect(jsonPath("$.colorPrimario").value("#112233"));
        assertThat(configuracion.vista(id).getColorPrimario()).isEqualTo("#112233");
        assertThat(configuracion.vista(id).getColorAcento()).isEqualTo("#AA5500");
        assertThat(configuracion.vista(1L).getColorPrimario()).isNotEqualTo("#112233");
        mvc.perform(get("/api/superadmin/apariencia/negocios").with(user("superadmin").roles("SUPERADMIN"))).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + id + ")].colorAcento").value(org.hamcrest.Matchers.hasItem("#AA5500")));
    }

    @Test
    void newBusinessesStartWithThePlatformColors() {
        marca.actualizar(new MarcaPlataformaService.DatosMarca("FEPZON", null, "#2E7D32", "#455A64", "#EF6C00"));
        String s = UUID.randomUUID().toString().substring(0, 6);
        PlataformaService.AccesoTemporal a = plataforma.crearNegocio(new PlataformaService.NuevoNegocio(
            "Nuevo " + s, "nue-" + s, "NORMAL", "ACTIVO", "MENSUAL", null, "adm." + s, "Dueño", null, null, null, null));
        assertThat(configuracion.vista(a.negocio().id()).getColorPrimario()).isEqualTo("#2E7D32");
    }
}
