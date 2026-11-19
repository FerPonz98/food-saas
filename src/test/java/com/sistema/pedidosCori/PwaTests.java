package com.sistema.pedidosCori;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PwaTests {
    @Autowired WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void manifestIsPublicAndOpensLogin() throws Exception {
        mvc().perform(get("/n/principal/manifest.webmanifest"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/manifest+json"))
            .andExpect(jsonPath("$.start_url").value("/n/principal/login"))
            .andExpect(jsonPath("$.display").value("standalone"))
            .andExpect(jsonPath("$.name").isNotEmpty())
            .andExpect(jsonPath("$.icons.length()").value(4));
    }

    @Test
    void iconsArePngOfRequestedSize() throws Exception {
        for (String ruta : new String[]{"/pwa/icon-192.png", "/pwa/icon-512.png", "/pwa/icon-180.png",
                "/pwa/icon-maskable-192.png", "/pwa/icon-maskable-512.png"}) {
            byte[] png = mvc().perform(get(ruta)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn().getResponse().getContentAsByteArray();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
            int esperado = Integer.parseInt(ruta.replaceAll("\\D", ""));
            assertThat(img.getWidth()).as(ruta).isEqualTo(esperado);
            assertThat(img.getHeight()).as(ruta).isEqualTo(esperado);
        }
    }

    @Test
    void unsupportedIconSizeIsNotFound() throws Exception {
        mvc().perform(get("/pwa/icon-64.png")).andExpect(status().isNotFound());
    }

    @Test
    void serviceWorkerAndOfflinePageArePublic() throws Exception {
        mvc().perform(get("/sw.js")).andExpect(status().isOk());
        mvc().perform(get("/pwa/offline.html")).andExpect(status().isOk());
    }

    @Test
    void businessManifestUsesItsOwnAddress() throws Exception {
        mvc().perform(get("/n/principal/manifest.webmanifest")).andExpect(status().isOk())
            .andExpect(jsonPath("$.icons[0].src").value(org.hamcrest.Matchers.startsWith("/n/principal/pwa/icon-192.png")));
        mvc().perform(get("/n/principal/pwa/icon-192.png")).andExpect(status().isOk())
            .andExpect(content().contentType("image/png"));
    }

    @Test
    void loginPageLinksManifest() throws Exception {
        mvc().perform(get("/n/principal/login")).andExpect(status().isOk())
            .andExpect(content().string(containsString("rel=\"manifest\"")))
            .andExpect(content().string(containsString("/js/pwa.js")));
    }
}
