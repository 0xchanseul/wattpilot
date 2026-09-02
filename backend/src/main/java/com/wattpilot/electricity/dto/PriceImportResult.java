package com.wattpilot.electricity.dto;

/**
 * Outcome of an {@code importPrices} call: how many hours were newly stored versus overwritten with
 * fresher data. Used by the caller (later, the price-fetch scheduler) for logging.
 */
public record PriceImportResult(int inserted, int updated) {

    public static PriceImportResult empty() {
        return new PriceImportResult(0, 0);
    }

    public int total() {
        return inserted + updated;
    }
}
