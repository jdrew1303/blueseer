package com.blueseer.pay;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTOs backing S-66 Remittance Overview, S-67 Payment Due Dates, S-68 Revenue Payments Record, S-69 Returns Look-Up. */
public final class RemittanceDtos {

    private RemittanceDtos() {
    }

    public record DateRange(LocalDate from, LocalDate to) {
    }

    public record RemittanceSummaryDTO(BigDecimal amountDue, LocalDate dueDate, String paymentStatus) {
    }

    public record PaymentDueDateDTO(String period, String amountCategory, LocalDate dueDate) {
    }

    public record PaymentRecordRowDTO(LocalDate date, BigDecimal amount, String reference, String method) {
    }

    public record ReturnSearchResultDTO(String returnType, String period, LocalDate submissionDate, String status) {
    }
}
