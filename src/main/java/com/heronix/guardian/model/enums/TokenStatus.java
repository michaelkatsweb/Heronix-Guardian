package com.heronix.guardian.model.enums;

/**
 * Status of a Guardian token.
 */
public enum TokenStatus {

    /**
     * Token is active and can be used
     */
    ACTIVE,

    /**
     * Token has expired (past expiration date)
     */
    EXPIRED,

    /**
     * Token was manually revoked
     */
    REVOKED,

    /**
     * Token was rotated and replaced by a new token
     */
    ROTATED
}
