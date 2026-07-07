package com.openfloat.mpesa.entity.enums;

/**
 * Lifecycle states of an M-Pesa transaction.
 */
public enum TransactionStatus {

    /** Transaction initiated, awaiting callback */
    PENDING,

    /** Transaction completed successfully */
    SUCCESS,

    /** Transaction failed */
    FAILED,

    /** Transaction has been reversed */
    REVERSED,

    /** Transaction timed out */
    TIMED_OUT,

    /** Callback already processed (idempotency) */
    ALREADY_PROCESSED
}
