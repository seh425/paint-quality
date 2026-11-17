package com.example.paintquality.auth;

import com.example.paintquality.user.InspectorUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "auth_tokens", indexes = {
        @Index(name = "idx_auth_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_auth_token_expiry", columnList = "expires_at")
})
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private InspectorUser user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AuthToken() {
    }

    public AuthToken(String tokenHash, InspectorUser user, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public InspectorUser getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
