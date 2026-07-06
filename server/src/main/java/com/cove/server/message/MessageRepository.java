package com.cove.server.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    @Query("select m from Message m where m.chatId = :chatId and m.createdAt < :before order by m.createdAt desc")
    List<Message> findPage(@Param("chatId") UUID chatId, @Param("before") Instant before, Pageable pageable);

    @Query("select m from Message m where m.chatId = :chatId order by m.createdAt desc")
    List<Message> findLatest(@Param("chatId") UUID chatId, Pageable pageable);

    Optional<Message> findTopByChatIdOrderByCreatedAtDesc(UUID chatId);

    long countByChatIdAndCreatedAtAfter(UUID chatId, Instant after);

    @Query("select count(m) > 0 from Message m, ChatMember cm " +
           "where m.attachmentId = :attachmentId " +
           "and cm.id.chatId = m.chatId " +
           "and cm.id.userId = :userId")
    boolean existsAccessibleAttachment(@Param("attachmentId") UUID attachmentId, @Param("userId") UUID userId);
}
