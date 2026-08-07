package com.openfloat.mpesa.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.openfloat.mpesa.dto.TransactionDto;
import com.openfloat.mpesa.dto.TransactionSearchCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for generating financial transaction reports in PDF and Excel (XLSX) formats.
 *
 * <p>Phase 9 — Component 1: Financial Report Generation (PDF & Excel Workbooks)</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final TransactionService transactionService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("UTC"));

    /**
     * Generates a branded PDF statement for transactions matching the search criteria.
     *
     * @param criteria Filter and search parameters
     * @return Byte array of the generated PDF document
     */
    public byte[] generatePdfReport(TransactionSearchCriteria criteria) {
        log.info("Generating PDF transaction report for criteria: {}", criteria);

        TransactionSearchCriteria reportCriteria = copyCriteriaForReport(criteria);
        Page<TransactionDto> page = transactionService.searchTransactions(reportCriteria);
        List<TransactionDto> transactions = page.getContent();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 30, 30);
            PdfWriter.getInstance(document, out);

            document.open();

            // Header Title
            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
            Paragraph title = new Paragraph("OpenFloat M-Pesa Transaction Statement", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(10);
            document.add(title);

            // Metadata Subheader
            com.lowagie.text.Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
            Paragraph meta = new Paragraph("Generated Total Records: " + transactions.size(), metaFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            meta.setSpacingAfter(20);
            document.add(meta);

            // Table setup (8 columns)
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3.5f, 2.0f, 2.5f, 2.5f, 2.0f, 2.5f, 2.0f, 3.0f});

            // Table Headers
            String[] headers = {"Txn ID", "Type", "Phone", "Account Ref", "Amount", "Status", "Recon Status", "Created At"};
            com.lowagie.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(header, headerFont));
                cell.setBackgroundColor(new Color(30, 41, 59)); // Slate dark header
                cell.setPadding(6);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // Table Rows
            com.lowagie.text.Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            for (TransactionDto txn : transactions) {
                table.addCell(createCell(txn.getTransactionId() != null ? txn.getTransactionId() : (txn.getId() != null ? txn.getId().toString().substring(0, 8) : "-"), cellFont));
                table.addCell(createCell(txn.getTransactionType() != null ? txn.getTransactionType().name() : "-", cellFont));
                table.addCell(createCell(txn.getPhoneNumber() != null ? txn.getPhoneNumber() : "-", cellFont));
                table.addCell(createCell(txn.getAccountReference() != null ? txn.getAccountReference() : "-", cellFont));
                table.addCell(createCell(txn.getAmount() != null ? "KES " + txn.getAmount().toString() : "0.00", cellFont));
                table.addCell(createCell(txn.getStatus() != null ? txn.getStatus().name() : "-", cellFont));
                table.addCell(createCell(txn.getReconciliationStatus() != null ? txn.getReconciliationStatus().name() : "-", cellFont));
                table.addCell(createCell(txn.getCreatedAt() != null ? DATE_FORMATTER.format(txn.getCreatedAt()) : "-", cellFont));
            }

            document.add(table);
            document.close();

            log.info("PDF transaction report generated successfully, byte size: {}", out.size());
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF transaction report", e);
        }
    }

    /**
     * Generates a styled Excel workbook (.xlsx) for transactions matching the search criteria.
     *
     * @param criteria Filter and search parameters
     * @return Byte array of the generated Excel workbook
     */
    public byte[] generateExcelReport(TransactionSearchCriteria criteria) {
        log.info("Generating Excel transaction report for criteria: {}", criteria);

        TransactionSearchCriteria reportCriteria = copyCriteriaForReport(criteria);
        Page<TransactionDto> page = transactionService.searchTransactions(reportCriteria);
        List<TransactionDto> transactions = page.getContent();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Transactions");

            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            // Data Style
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // Headers
            String[] headers = {"Txn ID", "Type", "Phone Number", "Paybill", "Account Reference", "Description", "Amount (KES)", "Status", "Reconciliation Status", "Created At"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data Rows
            int rowNum = 1;
            for (TransactionDto txn : transactions) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(txn.getTransactionId() != null ? txn.getTransactionId() : (txn.getId() != null ? txn.getId().toString() : "-"));
                row.createCell(1).setCellValue(txn.getTransactionType() != null ? txn.getTransactionType().name() : "-");
                row.createCell(2).setCellValue(txn.getPhoneNumber() != null ? txn.getPhoneNumber() : "-");
                row.createCell(3).setCellValue(txn.getPaybill() != null ? txn.getPaybill() : "-");
                row.createCell(4).setCellValue(txn.getAccountReference() != null ? txn.getAccountReference() : "-");
                row.createCell(5).setCellValue(txn.getDescription() != null ? txn.getDescription() : "-");
                row.createCell(6).setCellValue(txn.getAmount() != null ? txn.getAmount().doubleValue() : 0.00);
                row.createCell(7).setCellValue(txn.getStatus() != null ? txn.getStatus().name() : "-");
                row.createCell(8).setCellValue(txn.getReconciliationStatus() != null ? txn.getReconciliationStatus().name() : "-");
                row.createCell(9).setCellValue(txn.getCreatedAt() != null ? DATE_FORMATTER.format(txn.getCreatedAt()) : "-");

                for (int i = 0; i < headers.length; i++) {
                    row.getCell(i).setCellStyle(dataStyle);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            log.info("Excel transaction report generated successfully, byte size: {}", out.size());
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Excel report: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Excel transaction report", e);
        }
    }

    private PdfPCell createCell(String text, com.lowagie.text.Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private TransactionSearchCriteria copyCriteriaForReport(TransactionSearchCriteria original) {
        return TransactionSearchCriteria.builder()
                .startDate(original.getStartDate())
                .endDate(original.getEndDate())
                .paybill(original.getPaybill())
                .accountReference(original.getAccountReference())
                .phoneNumber(original.getPhoneNumber())
                .status(original.getStatus())
                .transactionType(original.getTransactionType())
                .reconciliationStatus(original.getReconciliationStatus())
                .page(0)
                .size(5000) // Report bulk limit
                .sortBy(original.getSortBy() != null ? original.getSortBy() : "createdAt")
                .sortDir(original.getSortDir() != null ? original.getSortDir() : "desc")
                .build();
    }
}
