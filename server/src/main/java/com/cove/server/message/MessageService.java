package com.cove.server.message;

import com.cove.server.chat.Chat;
import com.cove.server.chat.ChatRepository;
import com.cove.server.chat.ChatPin;
import com.cove.server.chat.ChatPinId;
import com.cove.server.chat.ChatPinRepository;
import com.cove.server.push.WebPushService;
import com.cove.server.ws.ChatEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final MessageReactionRepository reactionRepository;
    private final ChatRepository chatRepository;
    private final ChatPinRepository chatPinRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebPushService webPushService;

    public MessageService(MessageRepository messageRepository,
                          MessageReactionRepository reactionRepository,
                          ChatRepository chatRepository,
                          ChatPinRepository chatPinRepository,
                          SimpMessagingTemplate messagingTemplate,
                          WebPushService webPushService) {
        this.messageRepository = messageRepository;
        this.reactionRepository = reactionRepository;
        this.chatRepository = chatRepository;
        this.chatPinRepository = chatPinRepository;
        this.messagingTemplate = messagingTemplate;
        this.webPushService = webPushService;
    }

    @Transactional
    public Message send(Message message) {
        Message saved = messageRepository.save(message);
        chatRepository.findById(message.getChatId()).ifPresent(chat -> {
            chat.setLastMessageAt(message.getCreatedAt());
            chatRepository.save(chat);
        });
        messagingTemplate.convertAndSend("/topic/chats/" + message.getChatId(), ChatEvent.of("message_created", MessageResponse.from(saved)));
        return saved;
    }

    @Transactional
    public Message edit(UUID messageId, UUID userId, String ciphertext, String nonce) {
        Message message = messageRepository.findById(messageId).orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!message.getSenderId().equals(userId)) {
            throw new IllegalArgumentException("Not allowed");
        }
        message.setCiphertext(ciphertext);
        message.setNonce(nonce);
        message.setEditedAt(Instant.now());
        Message saved = messageRepository.save(message);
        messagingTemplate.convertAndSend("/topic/chats/" + message.getChatId(), ChatEvent.of("message_updated", MessageResponse.from(saved)));
        return saved;
    }

    @Transactional
    public void delete(UUID messageId, UUID userId) {
        Message message = messageRepository.findById(messageId).orElseThrow(() -> new IllegalArgumentException("Message not found"));
        if (!message.getSenderId().equals(userId)) {
            throw new IllegalArgumentException("Not allowed");
        }
        message.setDeletedAt(Instant.now());
        messageRepository.save(message);
        messagingTemplate.convertAndSend("/topic/chats/" + message.getChatId(), ChatEvent.of("message_deleted", Map.of(
            "id", messageId,
            "chatId", message.getChatId()
        )));
    }

    @Transactional
    public void addReaction(UUID messageId, UUID userId, String reaction) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            return;
        }
        MessageReactionId reactionId = new MessageReactionId(messageId, userId, reaction);
        String event;
        if (reactionRepository.existsById(reactionId)) {
            reactionRepository.deleteById(reactionId);
            event = "reaction_removed";
        } else {
            MessageReaction mr = new MessageReaction();
            mr.setId(reactionId);
            reactionRepository.save(mr);
            event = "reaction_added";
        }
        messagingTemplate.convertAndSend("/topic/chats/" + message.getChatId(), ChatEvent.of(event, Map.of(
            "messageId", messageId,
            "userId", userId,
            "reaction", reaction,
            "chatId", message.getChatId()
        )));
    }

    public void removeReaction(UUID messageId, UUID userId, String reaction) {
        reactionRepository.deleteById(new MessageReactionId(messageId, userId, reaction));
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message != null) {
            messagingTemplate.convertAndSend("/topic/chats/" + message.getChatId(), ChatEvent.of("reaction_removed", Map.of(
                "messageId", messageId,
                "userId", userId,
                "reaction", reaction,
                "chatId", message.getChatId()
            )));
        }
    }

    public void pin(UUID chatId, UUID messageId, UUID userId) {
        ChatPin pin = new ChatPin();
        pin.setId(new ChatPinId(chatId, messageId));
        pin.setPinnedBy(userId);
        chatPinRepository.save(pin);
        messagingTemplate.convertAndSend("/topic/chats/" + chatId, ChatEvent.of("message_pinned", Map.of(
            "messageId", messageId,
            "chatId", chatId
        )));
    }

    public void unpin(UUID chatId, UUID messageId) {
        chatPinRepository.deleteById(new ChatPinId(chatId, messageId));
        messagingTemplate.convertAndSend("/topic/chats/" + chatId, ChatEvent.of("message_unpinned", Map.of(
            "messageId", messageId,
            "chatId", chatId
        )));
    }

    public java.util.List<ReactionView> reactionsFor(UUID messageId) {
        return reactionRepository.findByMessageId(messageId).stream()
            .map(r -> new ReactionView(r.getId().getMessageId(), r.getId().getUserId(), r.getId().getReaction()))
            .toList();
    }

    public void pushToUser(UUID userId, Map<String, Object> payload) {
        if (webPushService.isEnabled()) {
            webPushService.sendToUser(userId, payload);
        }
    }

    public record MessageResponse(UUID id, UUID chatId, UUID senderId, String ciphertext, String nonce, int keyId,
                                  String type, UUID attachmentId, UUID replyToId, Instant createdAt, Instant editedAt, Instant deletedAt) {
        public static MessageResponse from(Message message) {
            return new MessageResponse(message.getId(), message.getChatId(), message.getSenderId(), message.getCiphertext(),
                message.getNonce(), message.getKeyId(), message.getType(), message.getAttachmentId(), message.getReplyToId(),
                message.getCreatedAt(), message.getEditedAt(), message.getDeletedAt());
        }
    }
}
