package com.heronix.guardian.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Token types for Guardian tokenization system.
 *
 * Token format: PREFIX_HASH_CHECKSUM
 * Example: STU_H7K2P9M3_X8
 */
@Getter
@RequiredArgsConstructor
public enum TokenType {

    /**
     * Student token - anonymizes student identity
     */
    STUDENT("STU", "STUDENT"),

    /**
     * Teacher token - anonymizes teacher identity
     */
    TEACHER("TCH", "TEACHER"),

    /**
     * Course token - anonymizes course identity
     */
    COURSE("CRS", "COURSE"),

    /**
     * Section token - anonymizes course section
     */
    SECTION("SEC", "SECTION"),

    /**
     * Assignment token - anonymizes assignment identity
     */
    ASSIGNMENT("ASN", "ASSIGNMENT");

    /**
     * 3-character prefix used in token value
     */
    private final String prefix;

    /**
     * Entity type name for database storage
     */
    private final String entityType;

    /**
     * Get TokenType from prefix string.
     *
     * @param prefix the 3-character prefix (e.g., "STU")
     * @return matching TokenType
     * @throws IllegalArgumentException if prefix not found
     */
    public static TokenType fromPrefix(String prefix) {
        for (TokenType type : values()) {
            if (type.prefix.equalsIgnoreCase(prefix)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown token prefix: " + prefix);
    }

    /**
     * Get TokenType from entity type string.
     *
     * @param entityType the entity type (e.g., "STUDENT")
     * @return matching TokenType
     * @throws IllegalArgumentException if entity type not found
     */
    public static TokenType fromEntityType(String entityType) {
        for (TokenType type : values()) {
            if (type.entityType.equalsIgnoreCase(entityType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown entity type: " + entityType);
    }
}
