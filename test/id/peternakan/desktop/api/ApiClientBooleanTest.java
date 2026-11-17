package id.peternakan.desktop.api;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ApiClientBooleanTest {
    @Test
    void readsBooleanFlagsFromJsonWithoutParsingThemAsNumbers() throws Exception {
        Map<?, ?> row = ApiClient.MAPPER.readValue(
                "{\"active\":true,\"must_change_password\":false,\"allocatable\":true}", Map.class);
        assertTrue(ApiClient.bool(row.get("active")));
        assertFalse(ApiClient.bool(row.get("must_change_password")));
        assertTrue(ApiClient.bool(row.get("allocatable")));
    }

    @Test
    void supportsNumericAndTextFlags() {
        for (Object value : List.of(true, 1, 1L, "true", "TRUE", "1")) {
            assertTrue(ApiClient.bool(value), value.toString());
        }
        for (Object value : List.of(false, 0, 0L, "false", "FALSE", "0")) {
            assertFalse(ApiClient.bool(value), value.toString());
        }
        assertFalse(ApiClient.bool(null));
        assertThrows(IllegalArgumentException.class, () -> ApiClient.bool("invalid"));
    }

    @Test
    void numericIdsRemainStrict() {
        assertEquals(42L, ApiClient.lng("42"));
        assertThrows(NumberFormatException.class, () -> ApiClient.lng(true));
    }
}
