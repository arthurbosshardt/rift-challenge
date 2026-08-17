package com.riftchallenge.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(name = "supabase_id", unique = true)
    private UUID supabaseId;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public static AppUser createFromSupabase(UUID supabaseId, String email, String username) {
        AppUser user = new AppUser();
        user.id = UUID.randomUUID();
        user.supabaseId = supabaseId;
        user.email = email;
        user.username = username;
        return user;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void linkSupabaseId(UUID supabaseId) {
        this.supabaseId = supabaseId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSupabaseId() {
        return supabaseId;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }
}
