package com.cove.server.chat;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ChatPinId implements Serializable {
    private UUID chatId;
    private UUID messageId;

    public ChatPinId() {}

    public ChatPinId(UUID chatId, UUID messageId) {
        this.chatId = chatId;
        this.messageId = messageId;
    }

    public UUID getChatId() { return chatId; }
    public void setChatId(UUID chatId) { this.chatId = chatId; }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatPinId that = (ChatPinId) o;
        return Objects.equals(chatId, that.chatId) && Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatId, messageId);
    }
}
