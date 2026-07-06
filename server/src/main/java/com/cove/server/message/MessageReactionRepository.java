package com.cove.server.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, MessageReactionId> {
    @Query("select mr from MessageReaction mr where mr.id.messageId = :messageId")
    List<MessageReaction> findByMessageId(@Param("messageId") UUID messageId);
}
