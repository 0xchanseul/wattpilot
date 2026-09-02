package com.wattpilot.electricity.provider;

/**
 * An external price provider could not be used for a given request: HTTP 4xx/5xx, a connect or read
 * timeout, an empty body, or a response that could not be parsed.
 *
 * <p>The collection flow catches this per area so one failing zone does not abort the others, and so
 * an external outage never propagates as an application error.
 */
public class ElectricityPriceProviderException extends RuntimeException {

    public ElectricityPriceProviderException(String message) {
        super(message);
    }

    public ElectricityPriceProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
