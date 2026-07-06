package com.cove.server.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    Optional<User> findByUsername(String username);

    @Query("select u from User u where (lower(u.username) like lower(concat('%', :q, '%')) " +
           "or lower(u.email) like lower(concat('%', :q, '%')) " +
           "or lower(u.phone) like lower(concat('%', :q, '%')) " +
           "or lower(u.displayName) like lower(concat('%', :q, '%')))")
    List<User> search(@Param("q") String query);

    @Query("select u from User u where u.id in :ids")
    List<User> findByIds(@Param("ids") List<UUID> ids);
}
