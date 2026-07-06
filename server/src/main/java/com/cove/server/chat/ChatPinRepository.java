package com.cove.server.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatPinRepository extends JpaRepository<ChatPin, ChatPinId> {
    @Query("select cp from ChatPin cp where cp.id.chatId = :chatId")
    List<ChatPin> findByChatId(@Param("chatId") UUID chatId);
}
