package com.openfloat.mpesa.entity.enums;

/**
 * Supported M-Pesa transaction types.
 */
public enum TransactionType {

    /** STK Push (Lipa na M-Pesa Online) */
    STK_PUSH,

    /** Customer to Business */
    C2B,

    /** Business to Customer */
    B2C,

    /** Transaction Reversal */
    REVERSAL
}
