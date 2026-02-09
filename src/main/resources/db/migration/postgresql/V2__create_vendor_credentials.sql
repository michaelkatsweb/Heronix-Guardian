-- Heronix Guardian - Vendor Credentials Schema
-- Version 1.0.0

-- Vendor Credentials table
-- Stores encrypted OAuth tokens and API keys for third-party integrations
CREATE TABLE vendor_credentials (
    id BIGSERIAL PRIMARY KEY,

    -- Vendor type (CANVAS, GOOGLE_CLASSROOM, etc.)
    vendor_type VARCHAR(30) NOT NULL,

    -- Friendly name for this connection
    connection_name VARCHAR(100) NOT NULL,

    -- API base URL
    api_base_url VARCHAR(255),

    -- Encrypted API key
    encrypted_api_key VARCHAR(1024),

    -- Encrypted OAuth tokens
    encrypted_oauth_token VARCHAR(2048),
    encrypted_refresh_token VARCHAR(1024),
    oauth_expires_at TIMESTAMP,

    -- OAuth client credentials
    client_id VARCHAR(255),
    encrypted_client_secret VARCHAR(1024),

    -- Webhook configuration
    webhook_url VARCHAR(512),
    encrypted_webhook_secret VARCHAR(256),

    -- Campus association (null = district-wide)
    campus_id BIGINT,

    -- Status
    active BOOLEAN DEFAULT TRUE,

    -- Sync tracking
    last_sync_at TIMESTAMP,
    last_sync_status VARCHAR(20),
    last_sync_error VARCHAR(500),

    -- Audit
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(100)
);

-- Indexes
CREATE INDEX idx_vendor_type ON vendor_credentials(vendor_type);
CREATE INDEX idx_vendor_active ON vendor_credentials(active);
CREATE INDEX idx_vendor_campus ON vendor_credentials(campus_id);
CREATE UNIQUE INDEX idx_vendor_connection_name ON vendor_credentials(connection_name);

-- Transmission Audit table
-- Logs all data transmissions to/from vendors
CREATE TABLE transmission_audit (
    id BIGSERIAL PRIMARY KEY,

    -- Unique transmission identifier
    transmission_id VARCHAR(36) NOT NULL UNIQUE,

    -- Vendor information
    vendor_type VARCHAR(30) NOT NULL,
    vendor_credential_id BIGINT,

    -- Direction (OUTBOUND, INBOUND)
    direction VARCHAR(15) NOT NULL,

    -- What was transmitted
    entity_type VARCHAR(20),
    token_count INT,

    -- Result
    success BOOLEAN NOT NULL,
    error_message TEXT,

    -- Metadata (no PII - just field names)
    fields_transmitted VARCHAR(500),
    fields_redacted VARCHAR(500),

    -- Timing
    transmitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    response_time_ms BIGINT,

    -- Request info
    ip_address VARCHAR(45),
    user_agent VARCHAR(255),

    FOREIGN KEY (vendor_credential_id) REFERENCES vendor_credentials(id)
);

-- Indexes for audit queries
CREATE INDEX idx_audit_timestamp ON transmission_audit(transmitted_at);
CREATE INDEX idx_audit_vendor ON transmission_audit(vendor_type);
CREATE INDEX idx_audit_direction ON transmission_audit(direction);
CREATE INDEX idx_audit_success ON transmission_audit(success);

-- Sync Jobs table
-- Tracks sync job status and progress
CREATE TABLE sync_jobs (
    id BIGSERIAL PRIMARY KEY,

    -- Unique job identifier
    job_id VARCHAR(36) NOT NULL UNIQUE,

    -- Vendor association
    vendor_credential_id BIGINT NOT NULL,

    -- Job details
    direction VARCHAR(15) NOT NULL,
    entity_type VARCHAR(20),

    -- Status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Progress
    records_total INT DEFAULT 0,
    records_processed INT DEFAULT 0,
    records_failed INT DEFAULT 0,

    -- Timing
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,

    -- Error info
    error_message TEXT,

    -- Audit
    created_by VARCHAR(100),

    FOREIGN KEY (vendor_credential_id) REFERENCES vendor_credentials(id)
);

-- Indexes for job queries
CREATE INDEX idx_sync_job_status ON sync_jobs(status);
CREATE INDEX idx_sync_job_vendor ON sync_jobs(vendor_credential_id);
CREATE INDEX idx_sync_job_started ON sync_jobs(started_at);
