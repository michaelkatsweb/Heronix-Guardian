-- ============================================================================
-- V3: Create Monitoring Tables for Guardian Monitor
-- ============================================================================
-- This migration creates tables for tracking and auditing data access requests
-- from third-party vendors like Canvas and Google Classroom.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Data Access Requests
-- Tracks every request for data from external vendors
-- ----------------------------------------------------------------------------
CREATE TABLE data_access_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Request identification
    request_id VARCHAR(36) NOT NULL UNIQUE,

    -- Vendor information
    vendor_type VARCHAR(30) NOT NULL,
    connection_name VARCHAR(100),

    -- Request details
    data_type VARCHAR(20) NOT NULL,
    direction VARCHAR(10) NOT NULL DEFAULT 'OUTBOUND',
    description VARCHAR(500),

    -- Context information
    course_name VARCHAR(200),
    teacher_display_name VARCHAR(100),
    grade_levels VARCHAR(50),
    school_year VARCHAR(9),

    -- Counts
    record_count INT DEFAULT 0,

    -- Status workflow
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requires_approval BOOLEAN DEFAULT FALSE,
    auto_approved BOOLEAN DEFAULT FALSE,

    -- Review tracking
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(100),

    -- Approval tracking
    approved_by VARCHAR(100),
    approved_at TIMESTAMP,

    -- Denial tracking
    denial_reason VARCHAR(500),

    -- Transmission tracking
    transmitted_at TIMESTAMP,
    source_ip VARCHAR(45),

    -- Notes
    notes TEXT,

    -- Audit timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX idx_dar_request_id ON data_access_requests(request_id);
CREATE INDEX idx_dar_vendor_type ON data_access_requests(vendor_type);
CREATE INDEX idx_dar_status ON data_access_requests(status);
CREATE INDEX idx_dar_created_at ON data_access_requests(created_at);
CREATE INDEX idx_dar_approved_at ON data_access_requests(approved_at);

-- ----------------------------------------------------------------------------
-- Token Mappings
-- Records individual token-to-entity mappings for each request
-- ----------------------------------------------------------------------------
CREATE TABLE token_mappings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Link to request
    request_id BIGINT NOT NULL,

    -- Token information
    token_value VARCHAR(20) NOT NULL,
    entity_type VARCHAR(20) NOT NULL,
    display_identifier VARCHAR(100) NOT NULL,
    context VARCHAR(200),
    entity_ref_hash VARCHAR(64),

    -- Transmission tracking
    transmitted BOOLEAN DEFAULT FALSE,

    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,

    -- Foreign key
    CONSTRAINT fk_tm_request
        FOREIGN KEY (request_id) REFERENCES data_access_requests(id) ON DELETE CASCADE
);

-- Indexes for lookups
CREATE INDEX idx_tm_request_id ON token_mappings(request_id);
CREATE INDEX idx_tm_token_value ON token_mappings(token_value);
CREATE INDEX idx_tm_entity_type ON token_mappings(entity_type);

-- ----------------------------------------------------------------------------
-- Request Audit Log
-- Detailed audit trail for all actions on requests
-- ----------------------------------------------------------------------------
CREATE TABLE request_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Link to request
    request_id BIGINT NOT NULL,

    -- Action details
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(100) NOT NULL,
    details TEXT,

    -- IP and context
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),

    -- Timestamp
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key
    CONSTRAINT fk_ral_request
        FOREIGN KEY (request_id) REFERENCES data_access_requests(id) ON DELETE CASCADE
);

CREATE INDEX idx_ral_request_id ON request_audit_log(request_id);
CREATE INDEX idx_ral_action ON request_audit_log(action);
CREATE INDEX idx_ral_performed_at ON request_audit_log(performed_at);
