package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.exception.ResourceNotFoundException;
import com.openfloat.mpesa.dto.CreateInvoiceRequestDto;
import com.openfloat.mpesa.dto.InvoiceResponseDto;
import com.openfloat.mpesa.entity.Invoice;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.InvoiceStatus;
import com.openfloat.mpesa.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceService — Customer Invoicing Engine & Auto Payment Fulfillment")
@SuppressWarnings("null")
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private InvoiceService invoiceService;

    private Invoice sampleInvoice;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository);

        sampleInvoice = Invoice.builder()
                .id(UUID.randomUUID())
                .invoiceNumber("INV-2026-12345")
                .customerName("Jane Doe")
                .customerPhone("254712345678")
                .customerEmail("jane.doe@example.com")
                .amount(new BigDecimal("5000.00"))
                .amountPaid(BigDecimal.ZERO)
                .currency("KES")
                .accountReference("INV-2026-12345")
                .description("Consulting Services Invoice")
                .dueDate(LocalDate.now().plusDays(14))
                .status(InvoiceStatus.UNPAID)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("createInvoice() saves invoice with correct status, initial amountPaid 0, and balance equal to amount")
    void createInvoice_validDto_createsInvoiceSuccessfully() {
        CreateInvoiceRequestDto dto = CreateInvoiceRequestDto.builder()
                .customerName("Jane Doe")
                .customerPhone("254712345678")
                .customerEmail("jane.doe@example.com")
                .amount(new BigDecimal("5000.00"))
                .dueDate(LocalDate.now().plusDays(14))
                .description("Consulting Services Invoice")
                .build();

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(UUID.randomUUID());
            inv.setCreatedAt(Instant.now());
            return inv;
        });

        InvoiceResponseDto response = invoiceService.createInvoice(dto);

        assertThat(response).isNotNull();
        assertThat(response.getInvoiceNumber()).startsWith("INV-2026-");
        assertThat(response.getCustomerPhone()).isEqualTo("254712345678");
        assertThat(response.getAmount()).isEqualByComparingTo("5000.00");
        assertThat(response.getAmountPaid()).isEqualByComparingTo("0.00");
        assertThat(response.getBalance()).isEqualByComparingTo("5000.00");
        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.UNPAID);

        verify(invoiceRepository, times(1)).save(any(Invoice.class));
    }

    @Test
    @DisplayName("getInvoice() returns DTO when invoice exists, throws ResourceNotFoundException when missing")
    void getInvoice_existingId_returnsInvoiceDto() {
        UUID id = sampleInvoice.getId();
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(sampleInvoice));

        InvoiceResponseDto dto = invoiceService.getInvoice(id);

        assertThat(dto).isNotNull();
        assertThat(dto.getInvoiceNumber()).isEqualTo("INV-2026-12345");
        assertThat(dto.getStatus()).isEqualTo(InvoiceStatus.UNPAID);
    }

    @Test
    @DisplayName("getInvoice() throws ResourceNotFoundException for non-existent ID")
    void getInvoice_notFound_throwsResourceNotFoundException() {
        UUID missingId = UUID.randomUUID();
        when(invoiceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getInvoice(missingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("cancelInvoice() transitions UNPAID invoice status to CANCELLED")
    void cancelInvoice_unpaidInvoice_cancelsSuccessfully() {
        UUID id = sampleInvoice.getId();
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(sampleInvoice));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

        InvoiceResponseDto response = invoiceService.cancelInvoice(id);

        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        verify(invoiceRepository, times(1)).save(sampleInvoice);
    }

    @Test
    @DisplayName("cancelInvoice() throws IllegalStateException when attempting to cancel a PAID invoice")
    void cancelInvoice_paidInvoice_throwsIllegalStateException() {
        sampleInvoice.setStatus(InvoiceStatus.PAID);
        UUID id = sampleInvoice.getId();
        when(invoiceRepository.findById(id)).thenReturn(Optional.of(sampleInvoice));

        assertThatThrownBy(() -> invoiceService.cancelInvoice(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel an invoice that has already been fully PAID");

        verify(invoiceRepository, never()).save(any(Invoice.class));
    }

    @Test
    @DisplayName("applyPaymentToAccountReference() fully settles invoice when payment amount matches or exceeds balance")
    void applyPaymentToAccountReference_fullPayment_marksInvoiceAsPaid() {
        Transaction mockTxn = Transaction.builder().id(UUID.randomUUID()).build();
        List<Invoice> pendingList = new ArrayList<>(List.of(sampleInvoice));

        when(invoiceRepository.findByAccountReferenceAndStatusIn(eq("INV-2026-12345"), anyList()))
                .thenReturn(pendingList);

        invoiceService.applyPaymentToAccountReference("INV-2026-12345", new BigDecimal("5000.00"), mockTxn);

        assertThat(sampleInvoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(sampleInvoice.getAmountPaid()).isEqualByComparingTo("5000.00");
        assertThat(sampleInvoice.getPaidAt()).isNotNull();
        assertThat(sampleInvoice.getTransaction()).isEqualTo(mockTxn);

        verify(invoiceRepository, times(1)).save(sampleInvoice);
    }

    @Test
    @DisplayName("applyPaymentToAccountReference() partially settles invoice when payment amount is less than balance")
    void applyPaymentToAccountReference_partialPayment_marksInvoiceAsPartial() {
        Transaction mockTxn = Transaction.builder().id(UUID.randomUUID()).build();
        List<Invoice> pendingList = new ArrayList<>(List.of(sampleInvoice));

        when(invoiceRepository.findByAccountReferenceAndStatusIn(eq("INV-2026-12345"), anyList()))
                .thenReturn(pendingList);

        invoiceService.applyPaymentToAccountReference("INV-2026-12345", new BigDecimal("2000.00"), mockTxn);

        assertThat(sampleInvoice.getStatus()).isEqualTo(InvoiceStatus.PARTIAL);
        assertThat(sampleInvoice.getAmountPaid()).isEqualByComparingTo("2000.00");
        assertThat(sampleInvoice.getBalance()).isEqualByComparingTo("3000.00");
        assertThat(sampleInvoice.getPaidAt()).isNull();

        verify(invoiceRepository, times(1)).save(sampleInvoice);
    }
}
