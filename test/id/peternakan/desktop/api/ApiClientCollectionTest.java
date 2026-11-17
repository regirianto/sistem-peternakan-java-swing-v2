package id.peternakan.desktop.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiClientCollectionTest {
    @Test
    void readsStringCollectionsAndCommaSeparatedDatabaseValues() {
        assertEquals(List.of("user.manage", "audit.read"),
                ApiClient.stringList(List.of("user.manage", "audit.read")));
        assertEquals(List.of("ADMIN", "OWNER"), ApiClient.stringList(Set.of("OWNER", "ADMIN"))
                .stream().sorted().toList());
        assertEquals(List.of("CUSTOMER", "SUPPLIER"),
                ApiClient.stringList("CUSTOMER,SUPPLIER"));
        assertEquals(List.of(), ApiClient.stringList(null));
    }

    @Test
    void readsObjectListsWithoutUncheckedElementCasts() {
        assertEquals("ADMIN", ApiClient.list(List.of(Map.of("code", "ADMIN"))).get(0).get("code"));
        assertThrows(IllegalArgumentException.class,
                () -> ApiClient.list(List.of("user.manage")));
    }
}
