package com.openfloat.mpesa.audit;

public enum AuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    PAYMENT_INITIATED,
    API_CALL,
    CONFIG_CHANGE,
    UNAUTHORIZED_ACCESS
}
