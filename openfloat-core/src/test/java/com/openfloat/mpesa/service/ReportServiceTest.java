package com.openfloat.mpesa.service;

import com.openfloat.mpesa.dto.TransactionDto;
import com.openfloat.mpesa.dto.TransactionSearchCriteria;
import com.openfloat.mpesa.entity.enums.ReconciliationStatus;
import com.openfloat.mpesa.entity.enums.TransactionStatus;
import com.openfloat.mpesa.entity.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService — PDF and Excel Financial Report Generation")
@SuppressWarnings("null")
class ReportServiceTest {

    @Mock
    private TransactionService transactionService;

    private ReportService reportService;

    private TransactionDto sampleTransaction;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(transactionService);

        sampleTransaction = TransactionDto.builder()
                .id(UUID.randomUUID())
                .transactionId("RQK78910AB")
                .transactionType(TransactionType.C2B)
                .phoneNumber("254712345678")
                .amount(new BigDecimal("2500.00"))
                .paybill("600980")
                .accountReference("INV-1002")
                .description("Payment for Invoice 1002")
                .status(TransactionStatus.SUCCESS)
                .reconciliationStatus(ReconciliationStatus.MATCHED)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("generatePdfReport() generates valid PDF binary starting with %PDF- header")
    void generatePdfReport_returnsValidPdfByteArray() {
        when(transactionService.searchTransactions(any(TransactionSearchCriteria.class)))
                .thenReturn(new PageImpl<>(List.of(sampleTransaction), PageRequest.of(0, 5000), 1));

        TransactionSearchCriteria criteria = new TransactionSearchCriteria();
        byte[] pdfBytes = reportService.generatePdfReport(criteria);

        assertThat(pdfBytes).isNotNull().isNotEmpty();
        // PDF header magic bytes "%PDF-"
        String header = new String(pdfBytes, 0, 5);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("generatePdfReport() handles empty transaction list gracefully")
    void generatePdfReport_emptyTransactions_returnsValidPdf() {
        when(transactionService.searchTransactions(any(TransactionSearchCriteria.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5000), 0));

        TransactionSearchCriteria criteria = new TransactionSearchCriteria();
        byte[] pdfBytes = reportService.generatePdfReport(criteria);

        assertThat(pdfBytes).isNotNull().isNotEmpty();
        String header = new String(pdfBytes, 0, 5);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("generateExcelReport() generates valid XLSX binary starting with PK (ZIP magic bytes)")
    void generateExcelReport_returnsValidExcelByteArray() {
        when(transactionService.searchTransactions(any(TransactionSearchCriteria.class)))
                .thenReturn(new PageImpl<>(List.of(sampleTransaction), PageRequest.of(0, 5000), 1));

        TransactionSearchCriteria criteria = new TransactionSearchCriteria();
        byte[] excelBytes = reportService.generateExcelReport(criteria);

        assertThat(excelBytes).isNotNull().isNotEmpty();
        // XLSX files are ZIP archives starting with magic bytes "PK" (0x50, 0x4B)
        assertThat(excelBytes[0]).isEqualTo((byte) 0x50);
        assertThat(excelBytes[1]).isEqualTo((byte) 0x4B);
    }

    @Test
    @DisplayName("generateExcelReport() handles empty transaction list gracefully")
    void generateExcelReport_emptyTransactions_returnsValidExcel() {
        when(transactionService.searchTransactions(any(TransactionSearchCriteria.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 5000), 0));

        TransactionSearchCriteria criteria = new TransactionSearchCriteria();
        byte[] excelBytes = reportService.generateExcelReport(criteria);

        assertThat(excelBytes).isNotNull().isNotEmpty();
        assertThat(excelBytes[0]).isEqualTo((byte) 0x50);
        assertThat(excelBytes[1]).isEqualTo((byte) 0x4B);
    }
}
