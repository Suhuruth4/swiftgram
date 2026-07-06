package com.cove.server.chat;

import com.cove.server.auth.AuthContext;
import com.cove.server.message.Message;
import com.cove.server.message.MessageRepository;
import com.cove.server.user.User;
import com.cove.server.user.UserRepository;
import com.cove.server.ws.ChatEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/chats")
public class ChatController {
    private final ChatService chatService;
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final ChatKeyRepository chatKeyRepository;
    private final ChatPinRepository chatPinRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatController(ChatService chatService,
                          ChatRepository chatRepository,
                          ChatMemberRepository chatMemberRepository,
                          ChatKeyRepository chatKeyRepository,
                          ChatPinRepository chatPinRepository,
                          MessageRepository messageRepository,
                          UserRepository userRepository,
                          SimpMessagingTemplate messagingTemplate) {
        this.chatService = chatService;
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
        this.chatKeyRepository = chatKeyRepository;
        this.chatPinRepository = chatPinRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public List<ChatSummary> list() {
        UUID userId = AuthContext.requireUserId();
        List<Chat> chats = chatService.listChats(userId);
        List<ChatSummary> result = new ArrayList<>();
        for (Chat chat : chats) {
            List<ChatMember> members = chatService.getMembers(chat.getId());
            ChatMember me = members.stream().filter(m -> m.getId().getUserId().equals(userId)).findFirst().orElse(null);
            long unread = me == null ? 0 : chatService.unreadCount(chat.getId(), me.getLastReadAt());
            Message lastMessage = chatService.lastMessage(chat.getId()).orElse(null);
            result.add(ChatSummary.from(chat, members, lastMessage, unread, userRepository));
        }
        return result;
    }

    @GetMapping("/{id}")
    public ChatDetail detail(@PathVariable("id") UUID id) {
        UUID userId = AuthContext.requireUserId();
        requireMember(id, userId);
        Chat chat = chatRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Chat not found"));
        List<ChatMember> members = chatService.getMembers(chat.getId());
        List<ChatPin> pins = chatPinRepository.findByChatId(chat.getId());
        return ChatDetail.from(chat, members, pins, userRepository);
    }

    @PostMapping
    public ChatSummary create(@Valid @RequestBody CreateChatRequest request) {
        UUID userId = AuthContext.requireUserId();
        Chat chat = chatService.createChat(userId, request.type(), request.title(), request.memberIds());
        List<ChatMember> members = chatService.getMembers(chat.getId());
        Message lastMessage = chatService.lastMessage(chat.getId()).orElse(null);
        return ChatSummary.from(chat, members, lastMessage, 0, userRepository);
    }

    @PostMapping("/{id}/members")
    public void addMember(@PathVariable("id") UUID chatId, @Valid @RequestBody AddMemberRequest request) {
        UUID userId = AuthContext.requireUserId();
        requireManager(chatId, userId);
        ChatMember cm = new ChatMember();
        cm.setId(new ChatMemberId(chatId, request.userId()));
        cm.setRole(request.role() != null ? request.role() : "member");
        chatMemberRepository.save(cm);
    }

    @DeleteMapping("/{id}/members/{userId}")
    public void removeMember(@PathVariable("id") UUID chatId, @PathVariable("userId") UUID targetUserId) {
        UUID userId = AuthContext.requireUserId();
        requireManager(chatId, userId);
        chatMemberRepository.deleteById(new ChatMemberId(chatId, targetUserId));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable("id") UUID chatId, @Valid @RequestBody ReadRequest request) {
        UUID userId = AuthContext.requireUserId();
        Instant readAt = request.readAt() != null ? request.readAt() : Instant.now();
        chatService.updateLastRead(chatId, userId, readAt);
        messagingTemplate.convertAndSend("/topic/chats/" + chatId, ChatEvent.of("read", Map.of(
            "chatId", chatId,
            "userId", userId,
            "readAt", readAt
        )));
    }

    @PostMapping("/{id}/keys")
    public void uploadKeys(@PathVariable("id") UUID chatId, @Valid @RequestBody ChatKeyUpload request) {
        UUID userId = AuthContext.requireUserId();
        requireMember(chatId, userId);
        for (ChatKeyEntry entry : request.entries()) {
            if (!userId.equals(entry.senderId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot upload keys as another user");
            }
            requireMember(chatId, entry.userId());
            if (chatKeyRepository.findKey(chatId, entry.userId(), request.keyId()).isPresent()) {
                continue;
            }
            ChatKey key = new ChatKey();
            key.setChatId(chatId);
            key.setUserId(entry.userId());
            key.setKeyId(request.keyId());
            key.setEncryptedKey(entry.encryptedKey());
            key.setNonce(entry.nonce());
            key.setSenderId(entry.senderId());
            chatKeyRepository.save(key);
        }
    }

    @GetMapping("/{id}/keys")
    public ChatKeyResponse getKey(@PathVariable("id") UUID chatId, @RequestParam("keyId") int keyId) {
        UUID userId = AuthContext.requireUserId();
        requireMember(chatId, userId);
        ChatKey key = chatKeyRepository.findKey(chatId, userId, keyId)
            .orElseThrow(() -> new IllegalArgumentException("Key not found"));
        return ChatKeyResponse.from(key);
    }

    @GetMapping("/{id}/pins")
    public List<PinnedMessage> pins(@PathVariable("id") UUID chatId) {
        UUID userId = AuthContext.requireUserId();
        requireMember(chatId, userId);
        List<ChatPin> pins = chatPinRepository.findByChatId(chatId);
        return pins.stream().map(PinnedMessage::from).collect(Collectors.toList());
    }

    private ChatMember requireMember(UUID chatId, UUID userId) {
        return chatMemberRepository.findById(new ChatMemberId(chatId, userId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a chat member"));
    }

    private void requireManager(UUID chatId, UUID userId) {
        ChatMember member = requireMember(chatId, userId);
        if (!"owner".equals(member.getRole()) && !"admin".equals(member.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to manage this chat");
        }
    }

    public record CreateChatRequest(@NotBlank String type, String title, @NotEmpty List<UUID> memberIds) {}
    public record AddMemberRequest(UUID userId, String role) {}
    public record ReadRequest(Instant readAt) {}

    public record ChatKeyUpload(int keyId, List<ChatKeyEntry> entries) {}
    public record ChatKeyEntry(UUID userId, String encryptedKey, String nonce, UUID senderId) {}
    public record ChatKeyResponse(UUID id, UUID chatId, UUID userId, int keyId, String encryptedKey, String nonce, UUID senderId) {
        public static ChatKeyResponse from(ChatKey key) {
            return new ChatKeyResponse(key.getId(), key.getChatId(), key.getUserId(), key.getKeyId(), key.getEncryptedKey(), key.getNonce(), key.getSenderId());
        }
    }

    public record ChatSummary(UUID id, String type, String title, Instant createdAt, Instant lastMessageAt,
                              long unreadCount, List<MemberView> members, MessageView lastMessage) {
        public static ChatSummary from(Chat chat, List<ChatMember> members, Message lastMessage, long unreadCount, UserRepository userRepository) {
            List<UUID> ids = members.stream().map(m -> m.getId().getUserId()).toList();
            Map<UUID, User> userMap = userRepository.findByIds(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
            List<MemberView> views = members.stream().map(m -> MemberView.from(m, userMap.get(m.getId().getUserId()))).toList();
            MessageView mv = lastMessage != null ? MessageView.from(lastMessage) : null;
            return new ChatSummary(chat.getId(), chat.getType(), chat.getTitle(), chat.getCreatedAt(), chat.getLastMessageAt(), unreadCount, views, mv);
        }
    }

    public record ChatDetail(UUID id, String type, String title, Instant createdAt, List<MemberView> members, List<PinnedMessage> pins) {
        public static ChatDetail from(Chat chat, List<ChatMember> members, List<ChatPin> pins, UserRepository userRepository) {
            List<UUID> ids = members.stream().map(m -> m.getId().getUserId()).toList();
            Map<UUID, User> userMap = userRepository.findByIds(ids).stream().collect(Collectors.toMap(User::getId, u -> u));
            List<MemberView> views = members.stream().map(m -> MemberView.from(m, userMap.get(m.getId().getUserId()))).toList();
            List<PinnedMessage> pinned = pins.stream().map(PinnedMessage::from).toList();
            return new ChatDetail(chat.getId(), chat.getType(), chat.getTitle(), chat.getCreatedAt(), views, pinned);
        }
    }

    public record MemberView(UUID userId, String displayName, String role, Instant joinedAt, Instant lastReadAt) {
        public static MemberView from(ChatMember member, User user) {
            String name = user != null ? user.getDisplayName() : null;
            return new MemberView(member.getId().getUserId(), name, member.getRole(), member.getJoinedAt(), member.getLastReadAt());
        }
    }

    public record MessageView(UUID id, UUID senderId, String ciphertext, String nonce, int keyId, String type,
                              UUID attachmentId, UUID replyToId, Instant createdAt, Instant editedAt, Instant deletedAt) {
        public static MessageView from(Message message) {
            return new MessageView(message.getId(), message.getSenderId(), message.getCiphertext(), message.getNonce(),
                message.getKeyId(), message.getType(), message.getAttachmentId(), message.getReplyToId(),
                message.getCreatedAt(), message.getEditedAt(), message.getDeletedAt());
        }
    }

    public record PinnedMessage(UUID chatId, UUID messageId, UUID pinnedBy, Instant pinnedAt) {
        public static PinnedMessage from(ChatPin pin) {
            return new PinnedMessage(pin.getId().getChatId(), pin.getId().getMessageId(), pin.getPinnedBy(), pin.getPinnedAt());
        }
    }
}
