package com.heronix.guardian.controller.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heronix.guardian.adapter.VendorAdapter;
import com.heronix.guardian.model.domain.VendorCredential;
import com.heronix.guardian.model.enums.VendorType;
import com.heronix.guardian.repository.VendorCredentialRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for managing vendor integrations.
 */
@RestController
@RequestMapping("/api/v1/guardian/vendors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Vendor Management", description = "APIs for managing third-party vendor integrations")
public class VendorManagementController {

    private final VendorCredentialRepository credentialRepository;
    private final List<VendorAdapter> vendorAdapters;

    @GetMapping
    @Operation(summary = "List all vendors", description = "Get all configured vendor integrations")
    @ApiResponse(responseCode = "200", description = "Vendors returned")
    public ResponseEntity<List<VendorCredentialDTO>> listVendors() {
        List<VendorCredential> credentials = credentialRepository.findAll();

        List<VendorCredentialDTO> response = credentials.stream()
                .map(VendorCredentialDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    @Operation(summary = "List active vendors", description = "Get all active vendor integrations")
    @ApiResponse(responseCode = "200", description = "Active vendors returned")
    public ResponseEntity<List<VendorCredentialDTO>> listActiveVendors() {
        List<VendorCredential> credentials = credentialRepository.findByActiveTrue();

        List<VendorCredentialDTO> response = credentials.stream()
                .map(VendorCredentialDTO::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get vendor details", description = "Get details for a specific vendor integration")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vendor found"),
        @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<VendorCredentialDTO> getVendor(@PathVariable Long id) {
        return credentialRepository.findById(id)
                .map(VendorCredentialDTO::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Operation(summary = "Create vendor", description = "Configure a new vendor integration")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Vendor created"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<VendorCredentialDTO> createVendor(@Valid @RequestBody VendorCreateRequest request) {
        log.info("Creating vendor integration: {} ({})", request.connectionName, request.vendorType);

        if (credentialRepository.existsByConnectionName(request.connectionName)) {
            return ResponseEntity.badRequest().build();
        }

        VendorCredential credential = VendorCredential.builder()
                .vendorType(request.vendorType)
                .connectionName(request.connectionName)
                .apiBaseUrl(request.apiBaseUrl)
                .clientId(request.clientId)
                .campusId(request.campusId)
                .active(true)
                .build();

        credential = credentialRepository.save(credential);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(VendorCredentialDTO.fromEntity(credential));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update vendor", description = "Update a vendor integration")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Vendor updated"),
        @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<VendorCredentialDTO> updateVendor(
            @PathVariable Long id,
            @Valid @RequestBody VendorUpdateRequest request) {

        return credentialRepository.findById(id)
                .map(credential -> {
                    if (request.connectionName != null) {
                        credential.setConnectionName(request.connectionName);
                    }
                    if (request.apiBaseUrl != null) {
                        credential.setApiBaseUrl(request.apiBaseUrl);
                    }
                    if (request.active != null) {
                        credential.setActive(request.active);
                    }
                    credential = credentialRepository.save(credential);
                    return ResponseEntity.ok(VendorCredentialDTO.fromEntity(credential));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete vendor", description = "Remove a vendor integration")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Vendor deleted"),
        @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<Void> deleteVendor(@PathVariable Long id) {
        if (!credentialRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        credentialRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Test connection", description = "Test the connection to a vendor")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connection test result")
    })
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        return credentialRepository.findById(id)
                .map(credential -> {
                    VendorAdapter adapter = findAdapter(credential.getVendorType());
                    if (adapter == null) {
                        Map<String, Object> error = new HashMap<>();
                        error.put("success", false);
                        error.put("message", "No adapter available for " + credential.getVendorType());
                        return ResponseEntity.ok(error);
                    }

                    var result = adapter.testConnection(credential);

                    Map<String, Object> response = new HashMap<>();
                    response.put("success", result.success());
                    response.put("message", result.message());
                    response.put("vendorVersion", result.vendorVersion());
                    response.put("responseTimeMs", result.responseTimeMs());

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate vendor", description = "Activate a vendor integration")
    public ResponseEntity<VendorCredentialDTO> activateVendor(@PathVariable Long id) {
        return credentialRepository.findById(id)
                .map(credential -> {
                    credential.setActive(true);
                    credential = credentialRepository.save(credential);
                    return ResponseEntity.ok(VendorCredentialDTO.fromEntity(credential));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate vendor", description = "Deactivate a vendor integration")
    public ResponseEntity<VendorCredentialDTO> deactivateVendor(@PathVariable Long id) {
        return credentialRepository.findById(id)
                .map(credential -> {
                    credential.setActive(false);
                    credential = credentialRepository.save(credential);
                    return ResponseEntity.ok(VendorCredentialDTO.fromEntity(credential));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/types")
    @Operation(summary = "List vendor types", description = "Get all supported vendor types")
    public ResponseEntity<List<Map<String, String>>> listVendorTypes() {
        List<Map<String, String>> types = java.util.Arrays.stream(VendorType.values())
                .map(type -> Map.of(
                        "type", type.name(),
                        "displayName", type.getDisplayName(),
                        "defaultApiUrl", type.getDefaultApiUrl()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(types);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private VendorAdapter findAdapter(VendorType type) {
        return vendorAdapters.stream()
                .filter(adapter -> adapter.getVendorType() == type)
                .findFirst()
                .orElse(null);
    }

    // ========================================================================
    // REQUEST/RESPONSE TYPES
    // ========================================================================

    public record VendorCreateRequest(
            VendorType vendorType,
            String connectionName,
            String apiBaseUrl,
            String clientId,
            Long campusId
    ) {}

    public record VendorUpdateRequest(
            String connectionName,
            String apiBaseUrl,
            Boolean active
    ) {}

    public record VendorCredentialDTO(
            Long id,
            VendorType vendorType,
            String connectionName,
            String apiBaseUrl,
            Long campusId,
            Boolean active,
            Boolean ready,
            String lastSyncAt,
            String lastSyncStatus
    ) {
        public static VendorCredentialDTO fromEntity(VendorCredential c) {
            return new VendorCredentialDTO(
                    c.getId(),
                    c.getVendorType(),
                    c.getConnectionName(),
                    c.getApiBaseUrl(),
                    c.getCampusId(),
                    c.getActive(),
                    c.isReady(),
                    c.getLastSyncAt() != null ? c.getLastSyncAt().toString() : null,
                    c.getLastSyncStatus()
            );
        }
    }
}
