
package com.tempchat.server.repository;

import com.tempchat.server.entity.ChatContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatContactRepository
        extends JpaRepository<ChatContact, Long> {

    List<ChatContact> findByOwner_LoginIdOrderByCreatedAtDesc(
            String loginId
    );

    boolean existsByOwner_IdAndContact_Id(
            Long ownerId,
            Long contactId
    );
}
