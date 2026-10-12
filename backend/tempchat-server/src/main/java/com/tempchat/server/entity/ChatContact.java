
package com.tempchat.server.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_contacts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_chat_contact_owner_contact",
                        columnNames = {"owner_id", "contact_id"}
                )
        }
)
public class ChatContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private User contact;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ChatContact() {
    }

    public ChatContact(User owner, User contact) {
        this.owner = owner;
        this.contact = contact;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public User getContact() {
        return contact;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
