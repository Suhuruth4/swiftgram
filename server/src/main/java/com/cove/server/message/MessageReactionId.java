package com.cove.server.message;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class MessageReactionId implements Serializable {
    private UUID messageId;
    private UUID userId;
    private String reaction;

    public MessageReactionId() {}

    public MessageReactionId(UUID messageId, UUID userId, String reaction) {
        this.messageId = messageId;
        this.userId = userId;
        this.reaction = reaction;
    }

    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getReaction() { return reaction; }
    public void setReaction(String reaction) { this.reaction = reaction; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessageReactionId that = (MessageReactionId) o;
        return Objects.equals(messageId, that.messageId) && Objects.equals(userId, that.userId) && Objects.equals(reaction, that.reaction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, userId, reaction);
    }
}
