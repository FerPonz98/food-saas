package com.sistema.pedidosCori.config;

import java.security.Principal;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.service.NegocioActual;
import com.sistema.pedidosCori.service.UsuarioSesion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    static final String TEMAS = "/topic/";
    private static final Pattern TEMA_DE_NEGOCIO = Pattern.compile("^/topic/n\\d+/.+");

    private final IUsuarioDao usuarios;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
        config.configureBrokerChannel().interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                return temaDeEnvio(message);
            }
        });
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                return temaDeSuscripcion(message);
            }
        });
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }

    public static String temaDe(long negocio, String destino) {
        return "/topic/n" + negocio + "/" + destino.substring(TEMAS.length());
    }

    public Message<?> temaDeEnvio(Message<?> message) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
        String destino = accessor.getDestination();
        if (accessor.getMessageType() != SimpMessageType.MESSAGE || destino == null || !destino.startsWith(TEMAS)
                || TEMA_DE_NEGOCIO.matcher(destino).matches()) return message;
        long negocio = NegocioActual.valor();
        if (negocio <= 0) {
            log.debug("Aviso a {} descartado: no hay un negocio en el contexto", destino);
            return null;
        }
        accessor.setDestination(temaDe(negocio, destino));
        return MessageBuilder.createMessage(message.getPayload(), accessor.getMessageHeaders());
    }

    public Message<?> temaDeSuscripcion(Message<?> message) {
        SimpMessageHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, SimpMessageHeaderAccessor.class);
        if (accessor == null) accessor = SimpMessageHeaderAccessor.wrap(message);
        SimpMessageType tipo = accessor.getMessageType();
        String destino = accessor.getDestination();
        if (tipo == SimpMessageType.MESSAGE && destino != null && destino.startsWith(TEMAS)) return null;
        if (tipo != SimpMessageType.SUBSCRIBE) return message;
        if (destino == null || !destino.startsWith(TEMAS) || TEMA_DE_NEGOCIO.matcher(destino).matches()) return null;
        Long negocio = negocioDe(accessor.getUser());
        if (negocio == null) return null;
        SimpMessageHeaderAccessor copia = SimpMessageHeaderAccessor.wrap(message);
        copia.setDestination(temaDe(negocio, destino));
        return MessageBuilder.createMessage(message.getPayload(), copia.getMessageHeaders());
    }

    private Long negocioDe(Principal usuario) {
        if (!(usuario instanceof Authentication auth) || !auth.isAuthenticated()) return null;
        if (auth.getPrincipal() instanceof UsuarioSesion sesion) return sesion.getNegocioId();
        return usuarios.findByUsername(auth.getName()).map(u -> u.getNegocioId()).orElse(null);
    }
}
