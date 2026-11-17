package id.peternakan.service.api;

public record ApiResponse(int status, String contentType, byte[] body, String filename) {
    public static ApiResponse pdf(byte[] body) {
        return new ApiResponse(200, "application/pdf", body, null);
    }

    public static ApiResponse csv(String body) {
        return new ApiResponse(200, "text/csv; charset=UTF-8",
                body.getBytes(java.nio.charset.StandardCharsets.UTF_8), null);
    }

    public static ApiResponse file(String contentType, byte[] body) {
        return new ApiResponse(200, contentType, body, null);
    }

    public static ApiResponse download(String contentType, byte[] body, String filename) {
        return new ApiResponse(200, contentType, body, filename);
    }
}
