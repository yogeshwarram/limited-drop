package com.limiteddrop.exception;

public class ReservationBusyException extends RuntimeException {
    public ReservationBusyException() { super("This drop is temporarily busy; retry with the same Idempotency-Key"); }
}
