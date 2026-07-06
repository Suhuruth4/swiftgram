package com.cove.server.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<Chat, UUID> {
    @Query("select c from Chat c where c.id in (select cm.id.chatId from ChatMember cm where cm.id.userId = :userId)")
    List<Chat> findChatsForUser(@Param("userId") UUID userId);
}
