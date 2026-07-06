package com.cove.server.chat;

import com.cove.server.message.Message;
import com.cove.server.message.MessageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatService {
    private final ChatRepository chatRepository;
    private final ChatMemberRepository chatMemberRepository;
    private final MessageRepository messageRepository;

    public ChatService(ChatRepository chatRepository, ChatMemberRepository chatMemberRepository, MessageRepository messageRepository) {
        this.chatRepository = chatRepository;
        this.chatMemberRepository = chatMemberRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public Chat createChat(UUID creatorId, String type, String title, List<UUID> memberIds) {
        Set<UUID> members = new HashSet<>(memberIds);
        members.add(creatorId);
        if ("direct".equalsIgnoreCase(type) && members.size() != 2) {
            throw new IllegalArgumentException("Direct chat must have exactly 2 members");
        }

        if ("direct".equalsIgnoreCase(type)) {
            Optional<Chat> existing = findDirectChat(members);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Chat chat = new Chat();
        chat.setType(type);
        chat.setTitle(title);
        chat.setCreatedBy(creatorId);
        chat.setCreatedAt(Instant.now());
        chat = chatRepository.save(chat);

        List<ChatMember> chatMembers = new ArrayList<>();
        for (UUID memberId : members) {
            ChatMember cm = new ChatMember();
            cm.setId(new ChatMemberId(chat.getId(), memberId));
            cm.setRole(memberId.equals(creatorId) ? "owner" : "member");
            cm.setJoinedAt(Instant.now());
            chatMembers.add(cm);
        }
        chatMemberRepository.saveAll(chatMembers);
        return chat;
    }

    public List<Chat> listChats(UUID userId) {
        return chatRepository.findChatsForUser(userId);
    }

    public Optional<ChatMember> getMembership(UUID chatId, UUID userId) {
        return chatMemberRepository.findById(new ChatMemberId(chatId, userId));
    }

    public List<ChatMember> getMembers(UUID chatId) {
        return chatMemberRepository.findByChatId(chatId);
    }

    @Transactional
    public void updateLastRead(UUID chatId, UUID userId, Instant readAt) {
        chatMemberRepository.findById(new ChatMemberId(chatId, userId)).ifPresent(member -> {
            member.setLastReadAt(readAt != null ? readAt : Instant.now());
            chatMemberRepository.save(member);
        });
    }

    public long unreadCount(UUID chatId, Instant lastReadAt) {
        if (lastReadAt == null) {
            return messageRepository.countByChatIdAndCreatedAtAfter(chatId, Instant.EPOCH);
        }
        return messageRepository.countByChatIdAndCreatedAtAfter(chatId, lastReadAt);
    }

    public Optional<Message> lastMessage(UUID chatId) {
        return messageRepository.findTopByChatIdOrderByCreatedAtDesc(chatId);
    }

    private Optional<Chat> findDirectChat(Set<UUID> members) {
        UUID any = members.iterator().next();
        List<Chat> chats = chatRepository.findChatsForUser(any);
        for (Chat chat : chats) {
            if (!"direct".equalsIgnoreCase(chat.getType())) continue;
            List<ChatMember> cm = chatMemberRepository.findByChatId(chat.getId());
            Set<UUID> ids = new HashSet<>();
            for (ChatMember m : cm) {
                ids.add(m.getId().getUserId());
            }
            if (ids.equals(members)) {
                return Optional.of(chat);
            }
        }
        return Optional.empty();
    }
}
