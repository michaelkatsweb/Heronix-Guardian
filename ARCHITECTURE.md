# Heronix Guardian - Architecture (Refactored v2.0.0)

## Overview

Heronix Guardian is a **Zero-Trust Third-Party Integration Layer** that protects student privacy when integrating with LMS vendors like Canvas and Google Classroom.

**Version 2.0.0** has been refactored to align with existing Heronix SIS architecture patterns.

## Key Architectural Changes (v2.0.0)

### What Changed

| Component | Before (v1.x) | After (v2.0) |
|-----------|---------------|--------------|
| **Tokenization** | Duplicate `GuardianToken` system | Uses existing `StudentTokenizationService` |
| **Gateway** | Direct WebClient calls to vendors | Routes through `SecureOutboundProxyService` |
| **Data Sanitization** | Minimal sanitization | Full `DataSanitizationService` integration |
| **LMS Integration** | Parallel implementation | Extends existing `LmsIntegrationService` |
| **Device Registry** | None | Vendors registered as SIS gateway devices |

### Why These Changes

1. **Single Source of Truth**: All student tokens now come from SIS's `StudentTokenizationService`
2. **Security Compliance**: All external data flows through the mandated `SecureOutboundProxyService`
3. **No Duplication**: Guardian extends existing infrastructure rather than duplicating it
4. **Audit Trail**: Uses SIS's `TransmissionAuditService` for complete logging

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              HERONIX SIS (Server 1)                         │
│  ┌─────────────────┐    ┌──────────────────────┐    ┌───────────────────┐  │
│  │ StudentRepository│───>│StudentTokenization   │───>│ SecureOutbound    │  │
│  │ CourseRepository │    │    Service           │    │  ProxyService     │  │
│  └─────────────────┘    └──────────────────────┘    └─────────┬─────────┘  │
│                                                                │            │
│  ┌─────────────────┐    ┌──────────────────────┐              │            │
│  │DataSanitization │    │  TransmissionAudit   │<─────────────┘            │
│  │    Service      │    │      Service         │                           │
│  └─────────────────┘    └──────────────────────┘                           │
└────────────────────────────────────────┬────────────────────────────────────┘
                                         │
                                         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           HERONIX GUARDIAN                                  │
│  ┌─────────────────────┐    ┌──────────────────────┐                       │
│  │  SisTokenBridge     │───>│ GuardianGateway      │                       │
│  │      Service        │    │     Service          │                       │
│  └─────────────────────┘    └──────────┬───────────┘                       │
│                                        │                                    │
│  ┌─────────────────────┐    ┌──────────▼───────────┐                       │
│  │  GuardianLms        │───>│ VendorDevice         │                       │
│  │      Service        │    │   Registry           │                       │
│  └─────────────────────┘    └──────────────────────┘                       │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                         VENDOR ADAPTERS                               │ │
│  │  ┌─────────────┐   ┌─────────────────────┐   ┌────────────────────┐  │ │
│  │  │CanvasAdapter│   │GoogleClassroomAdapter│   │ Future: Schoology │  │ │
│  │  └─────────────┘   └─────────────────────┘   └────────────────────┘  │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────┬────────────────────────────────────┘
                                         │
                                         ▼
                        ┌─────────────────────────────┐
                        │  EXTERNAL VENDORS (DMZ)     │
                        │  ┌───────┐   ┌───────────┐  │
                        │  │Canvas │   │ Google    │  │
                        │  │  LMS  │   │ Classroom │  │
                        │  └───────┘   └───────────┘  │
                        └─────────────────────────────┘
```

## Data Flow

### Outbound Flow (SIS → Vendor)

1. **LmsIntegrationService** receives sync request
2. **SisTokenBridgeService** tokenizes student data using existing `StudentTokenizationService`
3. **GuardianGatewayService** prepares transmission
4. **DataSanitizationService** removes any remaining PII
5. **SecureOutboundProxyService** encrypts and transmits
6. **TransmissionAuditService** logs the transmission

### Inbound Flow (Vendor → SIS)

1. Vendor sends tokenized grade data
2. **VendorAdapter** validates webhook signature
3. **SisTokenBridgeService** resolves tokens to student IDs
4. Grade data is stored in SIS (with real student IDs)

## Key Services

### SisTokenBridgeService
```java
// Bridges Guardian to SIS tokenization
StudentToken getOrCreateToken(Long studentId);
Optional<TokenizedStudentDTO> tokenizeStudent(Long studentId, VendorType vendorType);
Optional<Long> resolveToken(String tokenValue);
```

### GuardianGatewayService
```java
// Routes all transmissions through SIS gateway
TransmissionResult transmitStudent(TokenizedStudentDTO student, VendorCredential credential, String sourceIp);
BatchTransmissionResult transmitStudentBatch(List<TokenizedStudentDTO> students, ...);
```

### GuardianLmsService
```java
// Main orchestration service
ConnectionTestResult testConnection(LmsIntegration integration);
SyncResult syncRoster(LmsIntegration integration, List<Long> studentIds, String sourceIp);
List<InboundGradeDTO> fetchGrades(LmsIntegration integration, String courseToken);
```

### VendorDeviceRegistry
```java
// Registers vendors as SIS gateway devices
RegisteredDevice registerVendorDevice(VendorType vendorType, Long credentialId, String vendorName);
Optional<RegisteredDevice> getDeviceForVendor(VendorType vendorType, Long credentialId);
```

## Token Format

Guardian uses the existing SIS token format:
```
STU-XXXXXX  (e.g., STU-7A3F2E)
```

- **Prefix**: `STU` (Student), `TCH` (Teacher), `CRS` (Course)
- **Hash**: 6 hex characters (24 bits, 16.7M combinations)
- **Algorithm**: SHA-256(student_id + salt + timestamp + school_year)

## Deprecated Files (v1.x)

The following files from v1.x are deprecated and will be removed:
- `GuardianToken.java` - Use SIS `StudentToken` instead
- `TokenGenerationService.java` - Use `SisTokenBridgeService` instead
- `TokenValidationService.java` - Use `SisTokenBridgeService` instead
- `TokenMappingService.java` - Use `SisTokenBridgeService` instead
- `GuardianTokenRepository.java` - Use SIS `StudentTokenRepository` instead

## Configuration

```yaml
guardian:
  # Uses SIS tokenization
  use-sis-tokenization: true

  # Gateway integration
  gateway:
    enabled: true
    require-device-registration: true

  # Vendor configuration
  vendors:
    canvas:
      enabled: true
    google-classroom:
      enabled: true
```

## Security Model

1. **Zero PII to Vendors**: Vendors only receive tokens, never real student data
2. **Gateway Enforcement**: All transmissions go through `SecureOutboundProxyService`
3. **Device Registration**: Vendors must be registered as SIS gateway devices
4. **Data Sanitization**: Multiple layers of PII stripping
5. **Audit Trail**: Complete logging of all transmissions

## Migration from v1.x

1. Remove direct usage of `GuardianToken` and related services
2. Inject `SisTokenBridgeService` instead
3. Route transmissions through `GuardianGatewayService`
4. Register vendors as devices using `VendorDeviceRegistry`
