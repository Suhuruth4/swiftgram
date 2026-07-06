package com.cove.server.chat;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_pins")
public class ChatPin {
    @EmbeddedId
    private ChatPinId id;

    @Column(name = "pinned_by")
    private UUID pinnedBy;

    @Column(name = "pinned_at")
    private Instant pinnedAt = Instant.now();

    public ChatPinId getId() { return id; }
    public void setId(ChatPinId id) { this.id = id; }

    public UUID getPinnedBy() { return pinnedBy; }
    public void setPinnedBy(UUID pinnedBy) { this.pinnedBy = pinnedBy; }

    public Instant getPinnedAt() { return pinnedAt; }
    public void setPinnedAt(Instant pinnedAt) { this.pinnedAt = pinnedAt; }
}
