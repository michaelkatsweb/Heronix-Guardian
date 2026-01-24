package com.heronix.guardian.gateway;

/**
 * Registry for vendor devices in the SIS gateway.
 *
 * Each third-party vendor (Canvas, Google Classroom) must be registered as a
 * "device" in the SIS gateway before data can be transmitted to it.
 *
 * This follows the Heronix security model where ALL external recipients
 * must be pre-registered and authorized.
 *
 * Device Registration Process:
 * 1. Administrator registers vendor as a device in SIS Gateway
 * 2. Device gets unique ID, permissions, and encryption keys
 * 3. Guardian uses this registry to route transmissions
 *
 * STATUS: DISABLED in standalone mode - Requires Heronix-SIS on classpath
 *
 * To enable this service:
 * 1. Add heronix-sis dependency to pom.xml
 * 2. Set heronix.guardian.gateway.require-device-registration=true in application.yml
 * 3. Uncomment the @Service annotation and implementation
 *
 * In standalone mode, vendors are managed via VendorCredential without device registration.
 *
 * @author Heronix Development Team
 * @version 2.0.0
 */
public class VendorDeviceRegistry {
    // This class is intentionally empty in standalone mode.
    // When running with Heronix-SIS, this would contain the device registry implementation
    // that integrates with DeviceRegistrationService.
    //
    // See ARCHITECTURE.md for details on the integration pattern.
}
