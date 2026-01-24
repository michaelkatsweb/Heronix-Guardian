package com.heronix.guardian.model.dto;

import com.heronix.guardian.model.enums.TokenType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating a new token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenRequestDTO {

    /**
     * Type of entity to tokenize.
     */
    @NotNull(message = "Token type is required")
    private TokenType tokenType;

    /**
     * ID of the entity in Heronix SIS.
     */
    @NotNull(message = "Entity ID is required")
    private Long entityId;

    /**
     * Optional vendor scope. If null, creates a universal token.
     * Values: "CANVAS", "GOOGLE_CLASSROOM", etc.
     */
    private String vendorScope;
}
