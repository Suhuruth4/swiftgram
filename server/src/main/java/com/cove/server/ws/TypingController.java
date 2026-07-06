package com.cove.server.ws;

import com.cove.server.chat.ChatMemberId;
import com.cove.server.chat.ChatMemberRepository;
import com.cove.server.auth.UserPrincipal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
public class TypingController {
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMemberRepository chatMemberRepository;

    public TypingController(SimpMessagingTemplate messagingTemplate,
                            ChatMemberRepository chatMemberRepository) {
        this.messagingTemplate = messagingTemplate;
        this.chatMemberRepository = chatMemberRepository;
    }

    @MessageMapping("/typing")
    public void typing(TypingEvent event, Principal principal) {
        if (!(principal instanceof UserPrincipal up)) {
            throw new AccessDeniedException("Missing WebSocket user");
        }
        UUID userId = up.getId();
        if (chatMemberRepository.findById(new ChatMemberId(event.chatId(), userId)).isEmpty()) {
            throw new AccessDeniedException("Not a chat member");
        }
        TypingEvent payload = new TypingEvent(event.chatId(), userId, event.typing());
        messagingTemplate.convertAndSend("/topic/typing/" + event.chatId(), payload);
    }

    public record TypingEvent(UUID chatId, UUID userId, boolean typing) {}
}
