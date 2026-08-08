package com.openfloat.mpesa.service;

import com.openfloat.mpesa.common.exception.ResourceNotFoundException;
import com.openfloat.mpesa.dto.CreateInvoiceRequestDto;
import com.openfloat.mpesa.dto.InvoiceResponseDto;
import com.openfloat.mpesa.entity.Invoice;
import com.openfloat.mpesa.entity.Transaction;
import com.openfloat.mpesa.entity.enums.InvoiceStatus;
import com.openfloat.mpesa.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing customer invoices and automatic payment fulfillment matching.
 *
 * <p>Phase 9 — Component 3: Invoicing Engine & Payment Fulfillment</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final Random random = new Random();

    /**
     * Creates a new customer invoice and links it to an Account Reference.
     */
    @Transactional
    public InvoiceResponseDto createInvoice(CreateInvoiceRequestDto dto) {
        log.info("Creating new invoice for customer phone: {}, amount: KES {}", dto.getCustomerPhone(), dto.getAmount());

        String invoiceNumber = generateInvoiceNumber();
        String acctRef = dto.getAccountReference() != null && !dto.getAccountReference().isBlank()
                ? dto.getAccountReference()
                : invoiceNumber;

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumber)
                .customerName(dto.getCustomerName())
                .customerPhone(dto.getCustomerPhone())
                .customerEmail(dto.getCustomerEmail())
                .amount(dto.getAmount())
                .amountPaid(BigDecimal.ZERO)
                .currency("KES")
                .accountReference(acctRef)
                .description(dto.getDescription())
                .dueDate(dto.getDueDate())
                .status(InvoiceStatus.UNPAID)
                .build();

        Invoice saved = invoiceRepository.save(invoice);
        log.info("Invoice created successfully: invoiceNumber={}, id={}", saved.getInvoiceNumber(), saved.getId());

        return mapToDto(saved);
    }

    /**
     * Retrieves an invoice by its internal UUID.
     */
    public InvoiceResponseDto getInvoice(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));
        return mapToDto(invoice);
    }

    /**
     * Searches and filters invoices with pagination.
     */
    public Page<InvoiceResponseDto> searchInvoices(InvoiceStatus status, String customerPhone,
                                                   LocalDate dueDateFrom, LocalDate dueDateTo,
                                                   int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Invoice> invoicePage = invoiceRepository.searchInvoices(status, customerPhone, dueDateFrom, dueDateTo, pageable);

        List<InvoiceResponseDto> dtos = invoicePage.getContent().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, invoicePage.getTotalElements());
    }

    /**
     * Cancels an existing unpaid or partial invoice.
     */
    @Transactional
    public InvoiceResponseDto cancelInvoice(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("Cannot cancel an invoice that has already been fully PAID");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        Invoice updated = invoiceRepository.save(invoice);
        log.info("Invoice {} cancelled successfully", invoice.getInvoiceNumber());

        return mapToDto(updated);
    }

    /**
     * Automatically matches an incoming M-Pesa payment against pending invoices for the given account reference.
     * Transitions invoice status from UNPAID -> PARTIAL / PAID.
     *
     * @param accountReference Account Reference sent in M-Pesa callback
     * @param paymentAmount    Actual amount received from M-Pesa
     * @param transaction      Settling M-Pesa transaction
     */
    @Transactional
    public void applyPaymentToAccountReference(String accountReference, BigDecimal paymentAmount, Transaction transaction) {
        if (accountReference == null || accountReference.isBlank()) {
            return;
        }

        List<Invoice> pendingInvoices = invoiceRepository.findByAccountReferenceAndStatusIn(
                accountReference, List.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIAL)
        );

        if (pendingInvoices.isEmpty()) {
            log.debug("No pending invoices found for Account Reference: {}", accountReference);
            return;
        }

        BigDecimal remainingPayment = paymentAmount;

        for (Invoice invoice : pendingInvoices) {
            if (remainingPayment.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal currentBalance = invoice.getBalance();

            if (remainingPayment.compareTo(currentBalance) >= 0) {
                // Fully pay this invoice
                invoice.setAmountPaid(invoice.getAmount());
                invoice.setStatus(InvoiceStatus.PAID);
                invoice.setPaidAt(Instant.now());
                invoice.setTransaction(transaction);
                remainingPayment = remainingPayment.subtract(currentBalance);
                log.info("Invoice {} fully settled via M-Pesa payment", invoice.getInvoiceNumber());
            } else {
                // Partial payment
                invoice.setAmountPaid(invoice.getAmountPaid().add(remainingPayment));
                invoice.setStatus(InvoiceStatus.PARTIAL);
                invoice.setTransaction(transaction);
                log.info("Invoice {} partially settled. Paid so far: KES {}", invoice.getInvoiceNumber(), invoice.getAmountPaid());
                remainingPayment = BigDecimal.ZERO;
            }

            invoiceRepository.save(invoice);
        }
    }

    private String generateInvoiceNumber() {
        int year = LocalDate.now().getYear();
        int randomDigits = 10000 + random.nextInt(90000);
        return String.format("INV-%d-%d", year, randomDigits);
    }

    public InvoiceResponseDto mapToDto(Invoice invoice) {
        return InvoiceResponseDto.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .customerName(invoice.getCustomerName())
                .customerPhone(invoice.getCustomerPhone())
                .customerEmail(invoice.getCustomerEmail())
                .amount(invoice.getAmount())
                .amountPaid(invoice.getAmountPaid())
                .balance(invoice.getBalance())
                .currency(invoice.getCurrency())
                .accountReference(invoice.getAccountReference())
                .description(invoice.getDescription())
                .dueDate(invoice.getDueDate())
                .status(invoice.getStatus())
                .paidAt(invoice.getPaidAt())
                .transactionId(invoice.getTransaction() != null ? invoice.getTransaction().getId() : null)
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .build();
    }
}
