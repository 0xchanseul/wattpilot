package com.wattpilot.ev.service;

import com.wattpilot.common.exception.BusinessException;
import com.wattpilot.common.exception.ErrorCode;
import com.wattpilot.ev.dto.LinkVehicleConnectionRequest;
import com.wattpilot.ev.dto.VehicleCandidateResponse;
import com.wattpilot.ev.dto.VehicleConnectCandidatesRequest;
import com.wattpilot.ev.dto.VehicleConnectCandidatesResponse;
import com.wattpilot.ev.dto.VehicleConnectionResponse;
import com.wattpilot.ev.dto.VehicleTelemetryResponse;
import com.wattpilot.ev.entity.Ev;
import com.wattpilot.ev.entity.VehicleConnection;
import com.wattpilot.ev.repository.VehicleConnectionRepository;
import com.wattpilot.integration.smartcar.SmartcarClient;
import com.wattpilot.integration.smartcar.SmartcarProperties;
import com.wattpilot.integration.smartcar.SmartcarProviderException;
import com.wattpilot.integration.smartcar.dto.SmartcarTelemetry;
import com.wattpilot.integration.smartcar.dto.SmartcarVehicleCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Links a WattPilot EV to a Smartcar vehicle and surfaces its read-only telemetry. Charging
 * execution is unaffected: it stays on the Mock path regardless of whether an EV has a vehicle
 * connection (see docs/mvp-scope.md and TODO.md §3.3 for the read-only/V1.5 decision).
 */
@Service
@Transactional(readOnly = true)
public class VehicleConnectionService {

    private static final Logger log = LoggerFactory.getLogger(VehicleConnectionService.class);

    /**
     * One telemetry read costs four Smartcar calls. Serving a recent reading instead keeps repeated
     * requests from burning the provider's quota; battery state does not change meaningfully faster.
     */
    private static final Duration TELEMETRY_CACHE_TTL = Duration.ofSeconds(30);
    private static final int TELEMETRY_CACHE_MAX_ENTRIES = 500;

    private final VehicleConnectionRepository vehicleConnectionRepository;
    private final EvService evService;
    private final SmartcarClient smartcarClient;
    private final SmartcarConnectStateService stateService;
    private final SmartcarProperties properties;
    private final VehicleConnectionRemover remover;
    private final Clock clock;
    private final Map<String, CachedTelemetry> telemetryCache = new ConcurrentHashMap<>();

    public VehicleConnectionService(VehicleConnectionRepository vehicleConnectionRepository, EvService evService,
                                    SmartcarClient smartcarClient, SmartcarConnectStateService stateService,
                                    SmartcarProperties properties, VehicleConnectionRemover remover, Clock clock) {
        this.vehicleConnectionRepository = vehicleConnectionRepository;
        this.evService = evService;
        this.smartcarClient = smartcarClient;
        this.stateService = stateService;
        this.properties = properties;
        this.remover = remover;
        this.clock = clock;
    }

    public String buildConnectUrl(Long userId, Long evId) {
        requireEnabled();
        evService.getActiveOwnedEv(userId, evId);
        requireNotAlreadyConnected(evId);
        String state = stateService.issue(userId, evId);
        return smartcarClient.buildConnectUrl(state, String.valueOf(userId));
    }

    public VehicleConnectCandidatesResponse candidates(Long userId, VehicleConnectCandidatesRequest request) {
        requireEnabled();
        SmartcarConnectStateService.Verified verified = stateService.verify(request.state(), userId);
        evService.getActiveOwnedEv(userId, verified.evId());
        requireNotAlreadyConnected(verified.evId());

        List<VehicleCandidateResponse> vehicles = fetchCandidates(request.smartcarUserId()).stream()
                .map(VehicleCandidateResponse::from)
                .toList();
        return new VehicleConnectCandidatesResponse(verified.evId(), vehicles);
    }

    @Transactional
    public VehicleConnectionResponse link(Long userId, Long evId, LinkVehicleConnectionRequest request) {
        requireEnabled();
        SmartcarConnectStateService.Verified verified = stateService.verify(request.state(), userId);
        if (!verified.evId().equals(evId)) {
            throw new BusinessException(ErrorCode.INVALID_VEHICLE_CONNECT_STATE);
        }
        evService.getActiveOwnedEv(userId, evId);
        requireNotAlreadyConnected(evId);
        if (vehicleConnectionRepository.existsByUserIdAndSmartcarVehicleId(userId, request.smartcarVehicleId())) {
            throw new BusinessException(ErrorCode.VEHICLE_ALREADY_CONNECTED,
                    "This vehicle is already connected to another EV.");
        }

        SmartcarVehicleCandidate candidate = fetchCandidates(request.smartcarUserId()).stream()
                .filter(c -> c.smartcarVehicleId().equals(request.smartcarVehicleId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VEHICLE_CONNECT_STATE,
                        "The selected vehicle is not in this Smartcar account."));

        VehicleConnection connection = VehicleConnection.link(userId, evId, request.smartcarUserId(),
                candidate.smartcarVehicleId(), candidate.connectionId(), candidate.make(), candidate.model(),
                candidate.year());
        return VehicleConnectionResponse.from(vehicleConnectionRepository.save(connection));
    }

    public VehicleConnectionResponse get(Long userId, Long evId) {
        evService.getActiveOwnedEv(userId, evId);
        return VehicleConnectionResponse.from(getOwnedConnection(evId, userId));
    }

    public VehicleTelemetryResponse telemetry(Long userId, Long evId) {
        requireEnabled();
        evService.getActiveOwnedEv(userId, evId);
        // Ownership is checked above, before the cache is consulted. The key is the Smartcar vehicle
        // itself, so demo accounts that share the template's vehicle also share one reading.
        VehicleConnection connection = getOwnedConnection(evId, userId);
        String cacheKey = connection.getSmartcarUserId() + ":" + connection.getSmartcarVehicleId();
        Instant now = clock.instant();
        CachedTelemetry cached = telemetryCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return VehicleTelemetryResponse.from(cached.telemetry());
        }
        try {
            SmartcarTelemetry telemetry = smartcarClient.readTelemetry(
                    connection.getSmartcarUserId(), connection.getSmartcarVehicleId());
            cacheTelemetry(cacheKey, telemetry, now);
            return VehicleTelemetryResponse.from(telemetry);
        } catch (SmartcarProviderException ex) {
            log.warn("Smartcar telemetry read failed for ev {}: {}", evId, ex.getMessage());
            throw new BusinessException(ErrorCode.VEHICLE_PROVIDER_UNAVAILABLE);
        }
    }

    @Transactional
    public void disconnect(Long userId, Long evId) {
        Ev ev = evService.getActiveOwnedEv(userId, evId);
        EvService.requireUnlocked(ev, "The vehicle connection of this demo EV cannot be removed.");
        VehicleConnection connection = getOwnedConnection(evId, userId);
        remover.remove(connection);
    }

    private void cacheTelemetry(String cacheKey, SmartcarTelemetry telemetry, Instant now) {
        if (telemetryCache.size() >= TELEMETRY_CACHE_MAX_ENTRIES) {
            telemetryCache.values().removeIf(entry -> !entry.expiresAt().isAfter(now));
        }
        if (telemetryCache.size() < TELEMETRY_CACHE_MAX_ENTRIES) {
            telemetryCache.put(cacheKey, new CachedTelemetry(telemetry, now.plus(TELEMETRY_CACHE_TTL)));
        }
    }

    private record CachedTelemetry(SmartcarTelemetry telemetry, Instant expiresAt) {
    }

    private List<SmartcarVehicleCandidate> fetchCandidates(String smartcarUserId) {
        try {
            return smartcarClient.listVehicles(smartcarUserId);
        } catch (SmartcarProviderException ex) {
            log.warn("Smartcar listVehicles failed for smartcarUserId {}: {}", smartcarUserId, ex.getMessage());
            throw new BusinessException(ErrorCode.VEHICLE_PROVIDER_UNAVAILABLE);
        }
    }

    private VehicleConnection getOwnedConnection(Long evId, Long userId) {
        return vehicleConnectionRepository.findByEvIdAndUserId(evId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VEHICLE_CONNECTION_NOT_FOUND));
    }

    private void requireNotAlreadyConnected(Long evId) {
        if (vehicleConnectionRepository.existsByEvId(evId)) {
            throw new BusinessException(ErrorCode.VEHICLE_ALREADY_CONNECTED);
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new BusinessException(ErrorCode.VEHICLE_PROVIDER_NOT_CONFIGURED);
        }
    }
}
