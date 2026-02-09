-- Heronix Guardian - Token Storage Schema
-- Version 1.0.0

-- Guardian Tokens table
-- Stores anonymous tokens that map to real entity IDs
CREATE TABLE guardian_tokens (
    id BIGSERIAL PRIMARY KEY,

    -- Token value (e.g., STU_H7K2P9M3_X8)
    token_value VARCHAR(16) NOT NULL UNIQUE,

    -- Token type (STUDENT, TEACHER, COURSE, etc.)
    token_type VARCHAR(15) NOT NULL,

    -- Real entity ID in Heronix SIS
    entity_id BIGINT NOT NULL,

    -- Entity type string (denormalized for queries)
    entity_type VARCHAR(20) NOT NULL,

    -- Optional vendor scope (null = universal)
    vendor_scope VARCHAR(30),

    -- School year (e.g., "2025-2026")
    school_year VARCHAR(9) NOT NULL,

    -- Cryptographic salt for token generation
    salt VARCHAR(64) NOT NULL,

    -- Checksum for quick validation
    checksum VARCHAR(2) NOT NULL,

    -- Token status
    status VARCHAR(15) NOT NULL DEFAULT 'ACTIVE',

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    expires_at TIMESTAMP,

    -- Rotation tracking
    rotation_count INT DEFAULT 0,
    replaced_by_id BIGINT,

    -- Usage tracking
    last_used_at TIMESTAMP,
    usage_count BIGINT DEFAULT 0,

    -- Audit
    created_by VARCHAR(100)
);

-- Indexes for common queries
CREATE INDEX idx_guardian_token_value ON guardian_tokens(token_value);
CREATE INDEX idx_guardian_entity ON guardian_tokens(entity_type, entity_id);
CREATE INDEX idx_guardian_active ON guardian_tokens(status);
CREATE INDEX idx_guardian_vendor ON guardian_tokens(vendor_scope);
CREATE INDEX idx_guardian_school_year ON guardian_tokens(school_year);
CREATE INDEX idx_guardian_expires ON guardian_tokens(expires_at);
CREATE INDEX idx_guardian_type_status ON guardian_tokens(token_type, status);

-- Composite index for finding active tokens by entity
CREATE INDEX idx_guardian_entity_active ON guardian_tokens(entity_type, entity_id, status, vendor_scope);
