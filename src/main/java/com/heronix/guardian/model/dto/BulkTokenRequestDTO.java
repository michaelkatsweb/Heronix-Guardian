package com.heronix.guardian.model.dto;

import java.util.List;

import com.heronix.guardian.model.enums.TokenType;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating tokens in bulk.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkTokenRequestDTO {

    /**
     * Type of entities to tokenize.
     */
    @NotNull(message = "Token type is required")
    private TokenType tokenType;

    /**
     * List of entity IDs to tokenize.
     */
    @NotEmpty(message = "Entity IDs are required")
    private List<Long> entityIds;

    /**
     * Optional vendor scope. If null, creates universal tokens.
     */
    private String vendorScope;
}
