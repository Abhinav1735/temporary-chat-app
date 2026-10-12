package com.tempchat.server.repository;

import com.tempchat.server.entity.Role;
import com.tempchat.server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByRole(Role role);
}