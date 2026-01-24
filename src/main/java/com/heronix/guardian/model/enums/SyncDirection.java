package com.heronix.guardian.model.enums;

/**
 * Direction of data synchronization.
 */
public enum SyncDirection {

    /**
     * Data flowing from Heronix to vendor (push)
     */
    OUTBOUND,

    /**
     * Data flowing from vendor to Heronix (pull)
     */
    INBOUND,

    /**
     * Bidirectional sync
     */
    BIDIRECTIONAL
}
