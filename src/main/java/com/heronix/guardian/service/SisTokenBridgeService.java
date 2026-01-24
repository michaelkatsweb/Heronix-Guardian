package com.heronix.guardian.service;

/**
 * Bridge service that connects Guardian to the existing SIS tokenization system.
 *
 * This service replaces the duplicate GuardianToken system by delegating to
 * the existing StudentTokenizationService in Heronix-SIS.
 *
 * ARCHITECTURAL ALIGNMENT:
 * - Uses existing STU-XXXXXX token format from SIS
 * - Leverages existing token generation, rotation, and validation
 * - Maintains single source of truth for all student tokens
 *
 * STATUS: DISABLED in standalone mode - Requires Heronix-SIS on classpath
 *
 * To enable this service:
 * 1. Add heronix-sis dependency to pom.xml
 * 2. Set heronix.guardian.use-sis-tokenization=true in application.yml
 * 3. Uncomment the @Service annotation and implementation
 *
 * In standalone mode, Guardian uses its own tokenization via TokenGenerationService.
 *
 * @author Heronix Development Team
 * @version 2.0.0 - Refactored to use SIS tokenization
 */
public class SisTokenBridgeService {
    // This class is intentionally empty in standalone mode.
    // When running with Heronix-SIS, this would contain the bridge implementation
    // that delegates to StudentTokenizationService.
    //
    // See ARCHITECTURE.md for details on the integration pattern.
}
