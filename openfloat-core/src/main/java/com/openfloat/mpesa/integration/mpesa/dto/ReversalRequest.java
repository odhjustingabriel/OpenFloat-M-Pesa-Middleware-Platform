package com.openfloat.mpesa.integration.mpesa.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReversalRequest {

    @JsonProperty("Initiator")
    private String initiator;

    @JsonProperty("SecurityCredential")
    private String securityCredential;

    @JsonProperty("CommandID")
    private String commandId;

    @JsonProperty("TransactionID")
    private String transactionId;

    @JsonProperty("Amount")
    private Integer amount;

    @JsonProperty("Receiver")
    private String receiver;

    @JsonProperty("ReceiverIDType")
    private String receiverIdType;

    @JsonProperty("QueueTimeOutURL")
    private String queueTimeOutUrl;

    @JsonProperty("ResultURL")
    private String resultUrl;

    @JsonProperty("Remarks")
    private String remarks;

    @JsonProperty("Occasion")
    private String occasion;
}
