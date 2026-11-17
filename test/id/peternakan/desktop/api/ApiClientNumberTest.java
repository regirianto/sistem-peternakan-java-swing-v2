package id.peternakan.desktop.api;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClientNumberTest {
    @Test
    void numberTidakMemakaiNotasiIlmiah() {
        assertEquals("850000000", ApiClient.number(8.5E8));
        assertEquals("850000000", ApiClient.number(new BigDecimal("850000000.00")));
        assertEquals("1500.5", ApiClient.number(new BigDecimal("1500.50")));
        assertEquals("", ApiClient.number(null));
    }

    @Test
    void desimalJsonDibacaSebagaiBigDecimal() throws Exception {
        Object parsed = ApiClient.MAPPER.readValue("{\"remaining\":850000000.00}", java.util.Map.class);
        Object remaining = ((java.util.Map<?, ?>) parsed).get("remaining");
        assertTrue(remaining instanceof BigDecimal, "harus BigDecimal, bukan Double");
        assertEquals("850000000.00", remaining.toString());
    }
}
