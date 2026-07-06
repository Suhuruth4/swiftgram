package com.cove.server.message;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "message_reactions")
public class MessageReaction {
    @EmbeddedId
    private MessageReactionId id;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    public MessageReactionId getId() { return id; }
    public void setId(MessageReactionId id) { this.id = id; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
