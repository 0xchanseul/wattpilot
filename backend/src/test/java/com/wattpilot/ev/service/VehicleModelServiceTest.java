package com.wattpilot.ev.service;

import com.wattpilot.ev.dto.VehicleModelResponse;
import com.wattpilot.ev.entity.VehicleModel;
import com.wattpilot.ev.repository.VehicleModelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleModelServiceTest {

    @Mock
    private VehicleModelRepository vehicleModelRepository;

    private VehicleModelService vehicleModelService;

    @Test
    void searchWithABlankQueryDelegatesToFindAllJustLikeANullQuery() {
        vehicleModelService = new VehicleModelService(vehicleModelRepository);
        VehicleModel model = vehicleModel(1L, "BMW", "i4 eDrive40", "81.00", "11.00");
        when(vehicleModelRepository.findAllByOrderByManufacturerAscModelAsc()).thenReturn(List.of(model));

        List<VehicleModelResponse> result = vehicleModelService.search("   ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).manufacturer()).isEqualTo("BMW");
    }

    @Test
    void searchWithNoQueryDelegatesToFindAll() {
        vehicleModelService = new VehicleModelService(vehicleModelRepository);
        VehicleModel model = vehicleModel(1L, "BMW", "i4 eDrive40", "81.00", "11.00");
        when(vehicleModelRepository.findAllByOrderByManufacturerAscModelAsc()).thenReturn(List.of(model));

        List<VehicleModelResponse> result = vehicleModelService.search(null);

        assertThat(result).hasSize(1);
        verify(vehicleModelRepository).findAllByOrderByManufacturerAscModelAsc();
    }

    @Test
    void searchWithAQueryMatchesManufacturerOrModelCaseInsensitively() {
        vehicleModelService = new VehicleModelService(vehicleModelRepository);
        VehicleModel model = vehicleModel(2L, "Tesla", "Model Y Long Range", "75.00", "11.00");
        when(vehicleModelRepository
                .findByManufacturerContainingIgnoreCaseOrModelContainingIgnoreCaseOrderByManufacturerAscModelAsc(
                        "tesla", "tesla"))
                .thenReturn(List.of(model));

        List<VehicleModelResponse> result = vehicleModelService.search("tesla");

        assertThat(result).extracting(VehicleModelResponse::model).containsExactly("Model Y Long Range");
    }

    @Test
    void fromMapsAllPresetFieldsAndExcludesDefaultChargerPower() {
        vehicleModelService = new VehicleModelService(vehicleModelRepository);
        VehicleModel model = vehicleModel(3L, "Kia", "EV6 Long Range", "77.40", "11.00");
        when(vehicleModelRepository.findAllByOrderByManufacturerAscModelAsc()).thenReturn(List.of(model));

        VehicleModelResponse response = vehicleModelService.search(null).get(0);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.manufacturer()).isEqualTo("Kia");
        assertThat(response.model()).isEqualTo("EV6 Long Range");
        assertThat(response.batteryCapacityKwh()).isEqualByComparingTo("77.40");
        assertThat(response.maxAcChargingPowerKw()).isEqualByComparingTo("11.00");
    }

    // VehicleModel has no public factory: rows only ever come from Flyway-seeded data, never from
    // application code, so its constructor stays protected and tests build instances via reflection.
    private static VehicleModel vehicleModel(Long id, String manufacturer, String model,
                                              String batteryCapacityKwh, String maxAcChargingPowerKw) {
        VehicleModel vehicleModel;
        try {
            var constructor = VehicleModel.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            vehicleModel = constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        ReflectionTestUtils.setField(vehicleModel, "id", id);
        ReflectionTestUtils.setField(vehicleModel, "manufacturer", manufacturer);
        ReflectionTestUtils.setField(vehicleModel, "model", model);
        ReflectionTestUtils.setField(vehicleModel, "batteryCapacityKwh", new BigDecimal(batteryCapacityKwh));
        ReflectionTestUtils.setField(vehicleModel, "maxAcChargingPowerKw", new BigDecimal(maxAcChargingPowerKw));
        return vehicleModel;
    }
}
