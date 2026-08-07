package com.openfloat.mpesa.controller;

import com.openfloat.mpesa.dto.TransactionSearchCriteria;
import com.openfloat.mpesa.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Controller exposing financial report export endpoints (PDF & Excel workbooks).
 *
 * <p>Phase 9 — Component 1: Financial Report Generation (PDF & Excel Workbooks)</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Financial Reports", description = "Export transaction statements as PDF documents or Excel workbooks")
@SuppressWarnings("null")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/transactions/pdf")
    @Operation(summary = "Export filtered transactions as a branded PDF statement")
    public ResponseEntity<byte[]> exportPdfReport(@ModelAttribute TransactionSearchCriteria criteria) {
        log.info("Request received to export PDF transaction report with criteria: {}", criteria);
        byte[] pdfContent = reportService.generatePdfReport(criteria);

        String filename = "openfloat_statement_" + Instant.now().getEpochSecond() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfContent.length)
                .body(pdfContent);
    }

    @GetMapping("/transactions/excel")
    @Operation(summary = "Export filtered transactions as an Excel workbook (.xlsx)")
    public ResponseEntity<byte[]> exportExcelReport(@ModelAttribute TransactionSearchCriteria criteria) {
        log.info("Request received to export Excel transaction report with criteria: {}", criteria);
        byte[] excelContent = reportService.generateExcelReport(criteria);

        String filename = "openfloat_transactions_" + Instant.now().getEpochSecond() + ".xlsx";

        MediaType excelMediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(excelMediaType)
                .contentLength(excelContent.length)
                .body(excelContent);
    }
}
