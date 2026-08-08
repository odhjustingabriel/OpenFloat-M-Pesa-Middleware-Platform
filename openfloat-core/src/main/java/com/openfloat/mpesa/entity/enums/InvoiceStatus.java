package com.openfloat.mpesa.entity.enums;

/**
 * Lifecycle status of a customer invoice.
 *
 * <p>Phase 9 — Component 3: Invoicing Engine & Payment Fulfillment</p>
 */
public enum InvoiceStatus {

    /** Invoice has been created and is awaiting payment. */
    UNPAID,

    /** Partial payment has been received against the invoice. */
    PARTIAL,

    /** Invoice has been fully paid. */
    PAID,

    /** Invoice has been voided/cancelled by an operator. */
    CANCELLED,

    /** Invoice has passed its due date without being fully paid. */
    OVERDUE
}
