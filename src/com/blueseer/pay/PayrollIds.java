package com.blueseer.pay;

/** Typed id wrappers shared across the §4.1 repository/gateway interfaces. */
public final class PayrollIds {

    private PayrollIds() {
    }

    public record CompanyId(long value) {
    }

    public record EmployeeId(long value) {
    }

    public record DepartmentId(long value) {
    }

    public record LineItemId(long value) {
    }

    public record RegistrationId(long value) {
    }

    public record PsrBatchId(long value) {
    }

    public record PayslipId(long value) {
    }
}
