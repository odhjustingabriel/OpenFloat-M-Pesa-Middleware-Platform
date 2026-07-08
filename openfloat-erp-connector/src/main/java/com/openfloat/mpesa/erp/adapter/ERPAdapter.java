package com.openfloat.mpesa.erp.adapter;

import com.openfloat.mpesa.common.event.TransactionCompletedEvent;

/**
 * Interface representing a pluggable ERP system integration adapter.
 */
public interface ERPAdapter {

    /**
     * Dispatches a transaction event to the downstream ERP system.
     *
     * @param event the completed transaction event
     * @throws Exception if dispatch fails (will trigger retry policy)
     */
    void sendTransaction(TransactionCompletedEvent event) throws Exception;

    /**
     * Gets the unique system identifier for this ERP integration.
     *
     * @return the ERP system name (e.g. sap, oracle, dynamics, custom)
     */
    String getSystemName();
}
