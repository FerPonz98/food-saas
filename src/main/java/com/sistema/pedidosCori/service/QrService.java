package com.sistema.pedidosCori.service;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

@Service
public class QrService {
    private static final int MARGEN = 4;

    public String svg(String contenido) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 0);
        BitMatrix m;
        try {
            m = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0, hints);
        } catch (WriterException e) {
            throw new IllegalArgumentException("No se pudo generar el código QR", e);
        }
        int lado = m.getWidth() + MARGEN * 2;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < m.getHeight(); y++) {
            for (int x = 0; x < m.getWidth(); x++) {
                if (!m.get(x, y)) continue;
                int inicio = x;
                while (x + 1 < m.getWidth() && m.get(x + 1, y)) x++;
                path.append('M').append(inicio + MARGEN).append(' ').append(y + MARGEN)
                    .append('h').append(x - inicio + 1).append("v1h-").append(x - inicio + 1).append('z');
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + lado + " " + lado
            + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"Código QR\">"
            + "<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>"
            + "<path fill=\"#000\" d=\"" + path + "\"/></svg>";
    }

    public static String contenidoWifi(String nombre, String clave) {
        boolean abierta = clave == null || clave.isEmpty();
        return "WIFI:T:" + (abierta ? "nopass" : "WPA") + ";S:" + escapar(nombre) + ";"
            + (abierta ? "" : "P:" + escapar(clave) + ";") + ";";
    }

    private static String escapar(String valor) {
        return valor.replaceAll("([\\\\;,:\"])", "\\\\$1");
    }
}
