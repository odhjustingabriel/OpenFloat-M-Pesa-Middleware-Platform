package com.openfloat.mpesa.entity.enums;

/**
 * Reconciliation status for tracking transaction matching
 * against callback data, ERP responses, and external records.
 */
public enum ReconciliationStatus {

    /** Awaiting reconciliation */
    PENDING,

    /** Transaction matched across all systems */
    MATCHED,

    /** Discrepancy detected between systems */
    MISMATCHED,

    /** Reconciliation in progress */
    IN_PROGRESS
}
