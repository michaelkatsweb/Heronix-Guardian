-- ============================================================================
-- V4: Add Missing Columns to Monitoring Tables
-- ============================================================================
-- This migration adds columns that were missing from V3 to match the entity.
-- ============================================================================

-- Add missing columns to data_access_requests
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS connection_name VARCHAR(100);
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS description VARCHAR(500);
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS course_name VARCHAR(200);
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS teacher_display_name VARCHAR(100);
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS grade_levels VARCHAR(50);
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS school_year VARCHAR(9);
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS source_ip VARCHAR(45);
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS reviewed_by VARCHAR(100);
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS requires_approval BOOLEAN DEFAULT FALSE;
ALTER TABLE data_access_requests ADD COLUMN IF NOT EXISTS auto_approved BOOLEAN DEFAULT FALSE;

-- Add missing columns to token_mappings
ALTER TABLE token_mappings ADD COLUMN IF NOT EXISTS display_identifier VARCHAR(100);
ALTER TABLE token_mappings ADD COLUMN IF NOT EXISTS context VARCHAR(200);
ALTER TABLE token_mappings ADD COLUMN IF NOT EXISTS entity_ref_hash VARCHAR(64);
ALTER TABLE token_mappings ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;
ALTER TABLE token_mappings ADD COLUMN IF NOT EXISTS transmitted BOOLEAN DEFAULT FALSE;

-- Note: entity_identifier column migration removed as V3 already uses display_identifier
