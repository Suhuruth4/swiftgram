package com.cove.server.message;

import com.cove.server.auth.AuthContext;
import com.cove.server.chat.ChatMember;
import com.cove.server.chat.ChatMemberRepository;
import com.cove.server.chat.ChatMemberId;
import com.cove.server.storage.Attachment;
import com.cove.server.storage.AttachmentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
public class MessageController {
    private final MessageRepository messageRepository;
    private final MessageService messageService;
    private final ChatMemberRepository chatMemberRepository;
    private final AttachmentRepository attachmentRepository;

    public MessageController(MessageRepository messageRepository,
                             MessageService messageService,
                             ChatMemberRepository chatMemberRepository,
                             AttachmentRepository attachmentRepository) {
        this.messageRepository = messageRepository;
        this.messageService = messageService;
        this.chatMemberRepository = chatMemberRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @GetMapping("/chats/{chatId}/messages")
    public List<MessageWithReactions> list(@PathVariable("chatId") UUID chatId,
                                           @RequestParam(value = "before", required = false) Instant before,
                                           @RequestParam(value = "limit", defaultValue = "50") int limit) {
        UUID userId = AuthContext.requireUserId();
        ensureMember(chatId, userId);
        List<Message> messages;
        if (before != null) {
            messages = messageRepository.findPage(chatId, before, PageRequest.of(0, Math.min(100, limit)));
        } else {
            messages = messageRepository.findLatest(chatId, PageRequest.of(0, Math.min(100, limit)));
        }
        return messages.stream().map(message -> {
            List<ReactionView> reactions = messageService.reactionsFor(message.getId());
            return MessageWithReactions.from(message, reactions);
        }).toList();
    }

    @PostMapping("/chats/{chatId}/messages")
    public MessageService.MessageResponse send(@PathVariable("chatId") UUID chatId,
                                               @Valid @RequestBody SendMessageRequest request) {
        UUID userId = AuthContext.requireUserId();
        ensureMember(chatId, userId);
        ensureReplyBelongsToChat(request.replyToId(), chatId);
        ensureAttachmentCanBeSent(request.attachmentId(), userId);
        Message message = new Message();
        message.setChatId(chatId);
        message.setSenderId(userId);
        message.setCiphertext(request.ciphertext() != null ? request.ciphertext() : "");
        message.setNonce(request.nonce());
        message.setKeyId(request.keyId() > 0 ? request.keyId() : 1);
        message.setType(request.type());
        message.setAttachmentId(request.attachmentId());
        message.setReplyToId(request.replyToId());
        message.setClientId(request.clientId());
        Message saved = messageService.send(message);

        // push notification to other members
        List<ChatMember> members = chatMemberRepository.findByChatId(chatId);
        for (ChatMember member : members) {
            UUID memberId = member.getId().getUserId();
            if (!memberId.equals(userId)) {
                messageService.pushToUser(memberId, Map.of(
                    "type", "message",
                    "chatId", chatId.toString(),
                    "messageId", saved.getId().toString(),
                    "senderId", userId.toString()
                ));
            }
        }

        return MessageService.MessageResponse.from(saved);
    }

    @PutMapping("/messages/{id}")
    public MessageService.MessageResponse edit(@PathVariable("id") UUID id, @Valid @RequestBody EditMessageRequest request) {
        UUID userId = AuthContext.requireUserId();
        Message updated = messageService.edit(id, userId, request.ciphertext(), request.nonce());
        return MessageService.MessageResponse.from(updated);
    }

    @DeleteMapping("/messages/{id}")
    public void delete(@PathVariable("id") UUID id) {
        UUID userId = AuthContext.requireUserId();
        messageService.delete(id, userId);
    }

    @PostMapping("/messages/{id}/reactions")
    public void react(@PathVariable("id") UUID id, @Valid @RequestBody ReactionRequest request) {
        UUID userId = AuthContext.requireUserId();
        ensureMember(messageChatId(id), userId);
        messageService.addReaction(id, userId, request.reaction());
    }

    @DeleteMapping("/messages/{id}/reactions")
    public void unreact(@PathVariable("id") UUID id, @RequestParam("reaction") String reaction) {
        UUID userId = AuthContext.requireUserId();
        ensureMember(messageChatId(id), userId);
        messageService.removeReaction(id, userId, reaction);
    }

    @PostMapping("/messages/{id}/pin")
    public void pin(@PathVariable("id") UUID id, @RequestParam("chatId") UUID chatId) {
        UUID userId = AuthContext.requireUserId();
        UUID messageChatId = messageChatId(id);
        if (!messageChatId.equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message does not belong to this chat");
        }
        ensureMember(chatId, userId);
        messageService.pin(chatId, id, userId);
    }

    @DeleteMapping("/messages/{id}/pin")
    public void unpin(@PathVariable("id") UUID id, @RequestParam("chatId") UUID chatId) {
        UUID userId = AuthContext.requireUserId();
        UUID messageChatId = messageChatId(id);
        if (!messageChatId.equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message does not belong to this chat");
        }
        ensureMember(chatId, userId);
        messageService.unpin(chatId, id);
    }

    private void ensureMember(UUID chatId, UUID userId) {
        chatMemberRepository.findById(new ChatMemberId(chatId, userId))
            .orElseThrow(() -> new IllegalArgumentException("Not a chat member"));
    }

    private UUID messageChatId(UUID messageId) {
        return messageRepository.findById(messageId)
            .orElseThrow(() -> new IllegalArgumentException("Message not found"))
            .getChatId();
    }

    private void ensureReplyBelongsToChat(UUID replyToId, UUID chatId) {
        if (replyToId == null) {
            return;
        }
        UUID replyChatId = messageChatId(replyToId);
        if (!replyChatId.equals(chatId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reply target is not in this chat");
        }
    }

    private void ensureAttachmentCanBeSent(UUID attachmentId, UUID userId) {
        if (attachmentId == null) {
            return;
        }
        Attachment attachment = attachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new IllegalArgumentException("Attachment not found"));
        if (!userId.equals(attachment.getUploaderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attachment is not available to this user");
        }
    }

    public record SendMessageRequest(String ciphertext, @NotBlank String nonce,
                                     int keyId, @NotBlank String type,
                                     UUID attachmentId, UUID replyToId, String clientId) {}

    public record EditMessageRequest(@NotBlank String ciphertext, @NotBlank String nonce) {}

    public record ReactionRequest(@NotBlank String reaction) {}

    public record MessageWithReactions(UUID id, UUID chatId, UUID senderId, String ciphertext, String nonce, int keyId,
                                       String type, UUID attachmentId, UUID replyToId, Instant createdAt, Instant editedAt,
                                       Instant deletedAt, List<ReactionView> reactions) {
        public static MessageWithReactions from(Message message, List<ReactionView> reactions) {
            return new MessageWithReactions(message.getId(), message.getChatId(), message.getSenderId(), message.getCiphertext(),
                message.getNonce(), message.getKeyId(), message.getType(), message.getAttachmentId(), message.getReplyToId(),
                message.getCreatedAt(), message.getEditedAt(), message.getDeletedAt(), reactions);
        }
    }
}
