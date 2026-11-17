package id.peternakan.desktop.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Klien HTTP internal. Tidak pernah menyimpan kredensial MySQL; hanya token
 * sesi yang diterbitkan layanan.
 */
public final class ApiClient {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

    public record Download(String filename, String contentType, byte[] bytes) {
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final String baseUrl;
    private String token;
    private Map<String, Object> user = Map.of();
    private String lastError;
    private Long activeBranchId;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static ApiClient fromEnvironment() {
        String base = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8765");
        return new ApiClient(base);
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Map<String, Object> user() {
        return user;
    }

    public boolean loggedIn() {
        return token != null;
    }

    public Long activeBranchId() {
        return activeBranchId;
    }

    public void activeBranchId(Long branchId) {
        this.activeBranchId = branchId;
    }

    public String role() {
        return str(user.get("role"));
    }

    public String fullName() {
        return str(user.get("fullName"));
    }

    public boolean owner() {
        return "OWNER".equals(role());
    }

    public boolean mustChangePassword() {
        return Boolean.TRUE.equals(user.get("mustChangePassword"));
    }

    @SuppressWarnings("unchecked")
    public Set<String> permissions() {
        return new java.util.LinkedHashSet<>(stringList(user.get("permissions")));
    }

    public boolean can(String permission) {
        return permissions().contains(permission);
    }

    public String lastError() {
        return lastError;
    }

    public Map<String, Object> login(String username, String password) {
        Map<String, Object> result = post("/api/login", Map.of("username", username, "password", password));
        token = str(result.get("token"));
        @SuppressWarnings("unchecked")
        Map<String, Object> u = (Map<String, Object>) result.get("user");
        user = u;
        return result;
    }

    public void logout() {
        if (token != null) {
            try {
                post("/api/logout", Map.of());
            } catch (ApiClientException ignored) {
                // Sesi lokal tetap dibersihkan.
            }
        }
        token = null;
        user = Map.of();
        activeBranchId = null;
    }

    public Map<String, Object> bootstrapStatus() {
        return get("/api/bootstrap/status", Map.of());
    }

    public Map<String, Object> bootstrapOwner(String username, String password, String fullName) {
        return post("/api/bootstrap/owner", Map.of("username", username, "password", password,
                "fullName", fullName));
    }

    public Map<String, Object> me() {
        Map<String, Object> result = get("/api/me", Map.of());
        user = result;
        return result;
    }

    public Map<String, Object> changePassword(String currentPassword, String newPassword) {
        Map<String, Object> result = post("/api/change-password", Map.of("currentPassword", currentPassword,
                "newPassword", newPassword));
        me();
        return result;
    }

    public Map<String, Object> get(String path, Map<String, String> query) {
        return request("GET", path, query, null);
    }

    public Map<String, Object> post(String path, Object body) {
        return request("POST", path, Map.of(), body);
    }

    public Map<String, Object> post(String path, Object body, String requestId) {
        return requestWithId("POST", path, Map.of(), body, requestId);
    }

    public Map<String, Object> put(String path, Object body) {
        return request("PUT", path, Map.of(), body);
    }

    public Download download(String path, Map<String, String> query) {
        try {
            Map<String, String> scoped = scopedQuery(query);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path + queryString(scoped)))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/pdf,text/csv")
                    .GET();
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new ApiClientException(parseError(response.body()));
            }
            String disposition = response.headers().firstValue("Content-Disposition").orElse("");
            String filename = "laporan.pdf";
            int idx = disposition.indexOf("filename=");
            if (idx >= 0) {
                filename = disposition.substring(idx + 9).replace("\"", "").trim();
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
            return new Download(filename, contentType, response.body());
        } catch (ApiClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiClientException("Tidak dapat menghubungi layanan: " + e.getMessage());
        }
    }

    private Map<String, Object> requestWithId(String method, String path, Map<String, String> query,
                                              Object body, String requestId) {
        return request(method, path, query, body, requestId);
    }

    private Map<String, Object> request(String method, String path, Map<String, String> query, Object body) {
        return request(method, path, query, body, UUID.randomUUID().toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> request(String method, String path, Map<String, String> query, Object body,
                                        String requestId) {
        try {
            Map<String, String> scopedQuery = scopedQuery(query);
            Object scopedBody = body;
            if (activeBranchId != null && body instanceof Map<?, ?> map && !map.containsKey("branchId")) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>();
                map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                copy.put("branchId", activeBranchId);
                scopedBody = copy;
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path + queryString(scopedQuery)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("X-Request-Id", requestId);
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            String payload = scopedBody == null ? "{}" : MAPPER.writeValueAsString(scopedBody);
            builder.method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new ApiClientException(parseError(response.body()));
            }
            if (response.body() == null || response.body().length == 0) {
                return new LinkedHashMap<>();
            }
            Object parsed = MAPPER.readValue(response.body(), Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("data", parsed);
            return wrapper;
        } catch (ApiClientException e) {
            lastError = e.getMessage();
            throw e;
        } catch (Exception e) {
            lastError = "Tidak dapat menghubungi layanan: " + e.getMessage();
            throw new ApiClientException(lastError);
        }
    }

    private static String parseError(byte[] bytes) {
        try {
            Object parsed = MAPPER.readValue(bytes, Object.class);
            if (parsed instanceof Map<?, ?> map && map.get("error") != null) {
                return String.valueOf(map.get("error"));
            }
        } catch (Exception ignored) {
            // Fallback di bawah.
        }
        return "Permintaan gagal.";
    }

    private Map<String, String> scopedQuery(Map<String, String> query) {
        if (activeBranchId == null || query.containsKey("branchId")) {
            return query;
        }
        Map<String, String> scoped = new java.util.LinkedHashMap<>(query);
        scoped.put("branchId", String.valueOf(activeBranchId));
        return scoped;
    }

    private static String queryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("?");
        query.forEach((key, value) -> {
            if (value == null || value.isBlank()) {
                return;
            }
            if (sb.length() > 1) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return sb.length() == 1 ? "" : sb.toString();
    }

    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Angka untuk kolom input: tanpa notasi ilmiah (mis. 8.5E8) dan nol desimal. */
    public static String number(Object value) {
        if (value == null) {
            return "";
        }
        java.math.BigDecimal decimal;
        if (value instanceof java.math.BigDecimal b) {
            decimal = b;
        } else if (value instanceof Number n) {
            decimal = new java.math.BigDecimal(n.toString());
        } else {
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                return "";
            }
            try {
                decimal = new java.math.BigDecimal(text);
            } catch (NumberFormatException e) {
                return text;
            }
        }
        return decimal.stripTrailingZeros().toPlainString();
    }

    /** Flags from JSON/MySQL may be booleans or numeric 1/0. */
    public static boolean bool(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.longValue() == 1;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        throw new IllegalArgumentException("Nilai boolean tidak valid: " + text);
    }

    public static long lng(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    public static double dbl(Object value) {
        return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    public static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return new LinkedHashMap<>();
    }

    /** Konversi koleksi JSON atau teks dipisahkan koma ke daftar string. */
    public static java.util.List<String> stringList(Object value) {
        if (value instanceof java.util.Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return java.util.List.of();
            }
            return java.util.Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
        return java.util.List.of();
    }

    public static java.util.List<Map<String, Object>> list(Object value) {
        if (!(value instanceof java.util.List<?> values)) {
            return java.util.List.of();
        }
        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>(values.size());
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("Daftar objek berisi nilai non-objek: "
                        + (item == null ? "null" : item.getClass().getSimpleName()));
            }
            Map<String, Object> row = new LinkedHashMap<>();
            raw.forEach((key, itemValue) -> row.put(String.valueOf(key), itemValue));
            rows.add(row);
        }
        return rows;
    }
}
