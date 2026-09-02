package com.wattpilot.electricity.provider;

/**
 * The provider has no prices for the requested area and date yet. Next-day prices are typically
 * published in the early afternoon, so this is an expected outcome for an early collection run and
 * means "try again later", not "failed".
 */
public class PricesNotYetPublishedException extends ElectricityPriceProviderException {

    public PricesNotYetPublishedException(String message) {
        super(message);
    }
}
