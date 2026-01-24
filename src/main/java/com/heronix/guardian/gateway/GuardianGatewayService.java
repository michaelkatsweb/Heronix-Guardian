package com.heronix.guardian.gateway;

/**
 * Guardian Gateway Service - Routes all vendor communications through SIS Gateway.
 *
 * CRITICAL ARCHITECTURAL ALIGNMENT:
 * This service ensures ALL data leaving Heronix for third-party vendors
 * passes through the SecureOutboundProxyService - the single gateway
 * mandated by Heronix security architecture.
 *
 * Flow:
 * 1. Guardian receives tokenized data (already anonymized)
 * 2. Data is further sanitized via DataSanitizationService
 * 3. Transmission goes through SecureOutboundProxyService
 * 4. Audit trail is maintained by SIS gateway
 *
 * STATUS: DISABLED in standalone mode - Requires Heronix-SIS on classpath
 *
 * To enable this service:
 * 1. Add heronix-sis dependency to pom.xml
 * 2. Set heronix.guardian.gateway.enabled=true in application.yml
 * 3. Uncomment the @Service annotation and implementation
 *
 * In standalone mode, Guardian uses direct HTTP calls via the adapter classes.
 *
 * @author Heronix Development Team
 * @version 2.0.0 - Gateway-aligned implementation
 */
public class GuardianGatewayService {
    // This class is intentionally empty in standalone mode.
    // When running with Heronix-SIS, this would contain the gateway implementation
    // that routes all transmissions through SecureOutboundProxyService.
    //
    // See ARCHITECTURE.md for details on the integration pattern.
}
