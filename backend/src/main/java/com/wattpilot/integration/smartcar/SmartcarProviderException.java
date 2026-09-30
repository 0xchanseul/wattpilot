package com.wattpilot.integration.smartcar;

/**
 * A Smartcar call failed at the transport level (network, timeout, non-2xx status, unparseable
 * body). {@link com.wattpilot.ev.service.VehicleConnectionService} catches this and maps it onto
 * {@code VEHICLE_PROVIDER_UNAVAILABLE}; it never reaches the client directly.
 */
public class SmartcarProviderException extends RuntimeException {

    public SmartcarProviderException(String message) {
        super(message);
    }

    public SmartcarProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
