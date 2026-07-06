package com.cove.server.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatKeyRepository extends JpaRepository<ChatKey, UUID> {
    @Query("select ck from ChatKey ck where ck.chatId = :chatId and ck.userId = :userId and ck.keyId = :keyId")
    Optional<ChatKey> findKey(@Param("chatId") UUID chatId, @Param("userId") UUID userId, @Param("keyId") int keyId);

    @Query("select ck from ChatKey ck where ck.chatId = :chatId and ck.userId = :userId order by ck.keyId desc")
    List<ChatKey> findKeysForUser(@Param("chatId") UUID chatId, @Param("userId") UUID userId);
}
