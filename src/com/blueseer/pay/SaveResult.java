package com.blueseer.pay;

/** Common save outcome for the S-04-family tab Update actions: did this create or update the record. */
public record SaveResult(boolean wasCreate, String detailMessageOrNull) {
}
