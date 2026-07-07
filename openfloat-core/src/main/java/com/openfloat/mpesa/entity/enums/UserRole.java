package com.openfloat.mpesa.entity.enums;

/**
 * Role-Based Access Control roles for the platform.
 */
public enum UserRole {

    /** Read-only access to transactions and reports */
    VIEWER,

    /** Can initiate STK Push and B2C transactions */
    OPERATOR,

    /** Can access reconciliation tools and export financial data */
    FINANCE,

    /** Full administrative access: users, credentials, paybill configs, system settings */
    ADMIN
}
