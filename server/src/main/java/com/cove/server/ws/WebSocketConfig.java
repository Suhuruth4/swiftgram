package com.cove.server.ws;

import com.cove.server.chat.ChatMemberId;
import com.cove.server.chat.ChatMemberRepository;
import com.cove.server.auth.JwtService;
import com.cove.server.auth.UserPrincipal;
import com.cove.server.user.User;
import com.cove.server.user.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ChatMemberRepository chatMemberRepository;

    public WebSocketConfig(JwtService jwtService,
                           UserRepository userRepository,
                           ChatMemberRepository chatMemberRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.chatMemberRepository = chatMemberRepository;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new AccessDeniedException("Missing WebSocket token");
                    }
                    String token = authHeader.substring(7);
                    try {
                        Claims claims = jwtService.parse(token);
                        UUID userId = UUID.fromString(claims.getSubject());
                        Optional<User> userOpt = userRepository.findById(userId);
                        if (userOpt.isPresent()) {
                            User user = userOpt.get();
                            UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getPhone());
                            accessor.setUser(principal);
                        } else {
                            throw new AccessDeniedException("Invalid WebSocket user");
                        }
                    } catch (AccessDeniedException ex) {
                        throw ex;
                    } catch (Exception ex) {
                        throw new AccessDeniedException("Invalid WebSocket token");
                    }
                }
                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    Principal principal = accessor.getUser();
                    if (!(principal instanceof UserPrincipal userPrincipal)) {
                        throw new AccessDeniedException("Missing WebSocket user");
                    }
                    UUID chatId = chatIdFromDestination(accessor.getDestination());
                    if (chatId != null && chatMemberRepository.findById(new ChatMemberId(chatId, userPrincipal.getId())).isEmpty()) {
                        throw new AccessDeniedException("Not a chat member");
                    }
                }
                return message;
            }
        });
    }

    private UUID chatIdFromDestination(String destination) {
        if (destination == null) {
            return null;
        }
        String prefix = null;
        if (destination.startsWith("/topic/chats/")) {
            prefix = "/topic/chats/";
        }
        if (destination.startsWith("/topic/typing/")) {
            prefix = "/topic/typing/";
        }
        if (prefix == null) {
            return null;
        }
        try {
            return UUID.fromString(destination.substring(prefix.length()));
        } catch (IllegalArgumentException ex) {
            throw new AccessDeniedException("Invalid chat topic");
        }
    }
}
