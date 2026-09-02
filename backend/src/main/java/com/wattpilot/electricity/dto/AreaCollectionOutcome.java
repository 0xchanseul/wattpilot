package com.wattpilot.electricity.dto;

import com.wattpilot.common.PriceArea;

/**
 * Result of collecting one price area in a single scheduler run.
 *
 * @param area              the bidding zone
 * @param status            what happened
 * @param receivedSlotCount hours returned by the provider (0 unless {@code COLLECTED})
 * @param insertedCount     new rows written
 * @param updatedCount      existing rows overwritten
 * @param detail            failure reason or skip note; {@code null} on a clean collect
 */
public record AreaCollectionOutcome(
        PriceArea area,
        Status status,
        int receivedSlotCount,
        int insertedCount,
        int updatedCount,
        String detail
) {

    public enum Status {
        /** Prices were fetched and stored. */
        COLLECTED,
        /** The area was already stored in full for the date; no provider call was made. */
        ALREADY_COMPLETE,
        /** The provider has no data for the date yet; eligible for a later retry. */
        NOT_PUBLISHED,
        /** The provider call or the store failed; the other areas were unaffected. */
        FAILED
    }

    public static AreaCollectionOutcome collected(PriceArea area, int receivedSlotCount, PriceImportResult imported) {
        return new AreaCollectionOutcome(area, Status.COLLECTED, receivedSlotCount,
                imported.inserted(), imported.updated(), null);
    }

    public static AreaCollectionOutcome alreadyComplete(PriceArea area) {
        return new AreaCollectionOutcome(area, Status.ALREADY_COMPLETE, 0, 0, 0, null);
    }

    public static AreaCollectionOutcome notPublished(PriceArea area, String detail) {
        return new AreaCollectionOutcome(area, Status.NOT_PUBLISHED, 0, 0, 0, detail);
    }

    public static AreaCollectionOutcome failed(PriceArea area, String detail) {
        return new AreaCollectionOutcome(area, Status.FAILED, 0, 0, 0, detail);
    }
}
