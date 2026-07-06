package com.cove.server.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ChatMemberRepository extends JpaRepository<ChatMember, ChatMemberId> {
    @Query("select cm from ChatMember cm where cm.id.chatId = :chatId")
    List<ChatMember> findByChatId(@Param("chatId") UUID chatId);

    @Query("select cm from ChatMember cm where cm.id.userId = :userId")
    List<ChatMember> findByUserId(@Param("userId") UUID userId);
}
