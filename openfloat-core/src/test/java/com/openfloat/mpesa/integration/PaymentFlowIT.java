package com.openfloat.mpesa.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openfloat.mpesa.dto.StkPushRequestDto;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.integration.mpesa.DarajaClient;
import com.openfloat.mpesa.integration.mpesa.dto.StkPushResponse;
import com.openfloat.mpesa.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class PaymentFlowIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DarajaClient darajaClient;

    @Test
    void testFullStkPushPaymentFlow() throws Exception {
        // 1. Mock Daraja Client Response
        when(darajaClient.initiateStkPush(any())).thenReturn(StkPushResponse.builder()
                .merchantRequestId("MR-INT-001")
                .checkoutRequestId("ws_CO_INT_999")
                .responseCode("0")
                .responseDescription("Success")
                .build());

        // 2. Initiate STK Push via Controller Endpoint (secured with ROLE_ADMIN)
        StkPushRequestDto req = StkPushRequestDto.builder()
                .msisdn("0712345678")
                .amount(BigDecimal.TEN)
                .paybill("174379")
                .accountRef("ACC-INT-001")
                .description("IT Description")
                .build();

        mockMvc.perform(post("/api/v1/payments/stk-push")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // Verify transaction is persisted with status PENDING in Database
        Optional<Transaction> txOpt = transactionRepository.findByCheckoutRequestId("ws_CO_INT_999");
        assertThat(txOpt).isPresent();
        Transaction transaction = txOpt.get();
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.getMerchantRequestId()).isEqualTo("MR-INT-001");

        // 3. Simulate callback from Safaricom (public endpoint, no JWT needed)
        Map<String, Object> item1 = Map.of("Name", "MpesaReceiptNumber", "Value", "RCPT_IT_999");
        Map<String, Object> item2 = Map.of("Name", "Amount", "Value", 10.00);
        Map<String, Object> metadata = Map.of("Item", List.of(item1, item2));

        Map<String, Object> stkCallback = Map.of(
                "MerchantRequestID", "MR-INT-001",
                "CheckoutRequestID", "ws_CO_INT_999",
                "ResultCode", 0,
                "ResultDesc", "Success confirmation",
                "CallbackMetadata", metadata
        );
        Map<String, Object> callbackPayload = Map.of("Body", Map.of("stkCallback", stkCallback));

        mockMvc.perform(post("/api/v1/mpesa/callbacks/stk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callbackPayload)))
                .andExpect(status().isOk());

        // 4. Verify Database state has updated to SUCCESS
        Optional<Transaction> updatedTxOpt = transactionRepository.findByCheckoutRequestId("ws_CO_INT_999");
        assertThat(updatedTxOpt).isPresent();
        Transaction updatedTransaction = updatedTxOpt.get();
        assertThat(updatedTransaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(updatedTransaction.getTransactionId()).isEqualTo("RCPT_IT_999");
        assertThat(updatedTransaction.getReconciliationId()).isEqualTo("REC-RCPT_IT_999");
    }
}
