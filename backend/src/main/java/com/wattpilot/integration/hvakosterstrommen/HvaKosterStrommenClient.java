package com.wattpilot.integration.hvakosterstrommen;

import com.wattpilot.common.PriceArea;
import com.wattpilot.electricity.dto.PriceSlot;
import com.wattpilot.electricity.provider.ElectricityPriceProvider;
import com.wattpilot.electricity.provider.ElectricityPriceProviderException;
import com.wattpilot.electricity.provider.PricesNotYetPublishedException;
import com.wattpilot.integration.hvakosterstrommen.dto.HvaKosterStrommenPrice;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link ElectricityPriceProvider} backed by the public Hva koster strømmen API.
 *
 * <p>The request URL carries the date and area in its path, e.g.
 * {@code /prices/2026/09-03_NO1.json} (month and day always two digits). A missing file (HTTP 404)
 * means next-day prices are not published yet and maps to {@link PricesNotYetPublishedException};
 * every other transport or parsing failure maps to {@link ElectricityPriceProviderException} so the
 * caller can isolate it.
 */
@Component
public class HvaKosterStrommenClient implements ElectricityPriceProvider {

    private static final ParameterizedTypeReference<List<HvaKosterStrommenPrice>> PRICE_LIST_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final String CURRENCY_NOK = "NOK";

    private final RestClient restClient;

    public HvaKosterStrommenClient(RestClient hvaKosterStrommenRestClient) {
        this.restClient = hvaKosterStrommenRestClient;
    }

    @Override
    public List<PriceSlot> fetchDailyPrices(PriceArea priceArea, LocalDate date) {
        String path = buildPath(priceArea, date);
        try {
            List<HvaKosterStrommenPrice> body = restClient.get()
                    .uri(path)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> readBody(priceArea, date, path, response));
            return toPriceSlots(body);
        } catch (ElectricityPriceProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ElectricityPriceProviderException(
                    "Failed to fetch prices for %s on %s".formatted(priceArea, date), ex);
        }
    }

    /**
     * Path relative to the configured base URL. Package-private so the URL contract can be asserted
     * directly in tests.
     */
    static String buildPath(PriceArea priceArea, LocalDate date) {
        return "/prices/%d/%02d-%02d_%s.json".formatted(
                date.getYear(), date.getMonthValue(), date.getDayOfMonth(), priceArea.name());
    }

    private static List<HvaKosterStrommenPrice> readBody(PriceArea priceArea, LocalDate date, String path,
                                                         RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        HttpStatusCode status = statusOf(response);
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            throw new PricesNotYetPublishedException(
                    "Hva koster strømmen has no prices for %s on %s yet".formatted(priceArea, date));
        }
        if (status.isError()) {
            throw new ElectricityPriceProviderException(
                    "Hva koster strømmen returned HTTP %d for %s".formatted(status.value(), path));
        }
        return response.bodyTo(PRICE_LIST_TYPE);
    }

    private static HttpStatusCode statusOf(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            return response.getStatusCode();
        } catch (java.io.IOException ex) {
            throw new ElectricityPriceProviderException("Unable to read response status", ex);
        }
    }

    private static List<PriceSlot> toPriceSlots(List<HvaKosterStrommenPrice> body) {
        if (body == null || body.isEmpty()) {
            return List.of();
        }
        return body.stream()
                .map(price -> new PriceSlot(price.timeStart(), price.timeEnd(), price.nokPerKwh(), CURRENCY_NOK))
                .toList();
    }
}
