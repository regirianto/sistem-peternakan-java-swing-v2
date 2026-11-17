package id.peternakan.desktop.ui;

import com.sun.net.httpserver.HttpServer;
import id.peternakan.desktop.api.ApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@EnabledIfSystemProperty(named = "ui.tests", matches = "true", disabledReason = "Run with -Dui.tests=true in a desktop session")
class WindowNavigationTest {
    @Test
    void navigationKeepsEveryViewRegisteredAndDisposingLoginKeepsMainOpen() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Requires a desktop display");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "{\"items\":[],\"needsBootstrap\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            ApiClient client = client(server, List.of("dashboard.view", "goat.read", "pen.manage",
                    "supplier.manage", "purchase.manage", "sale.manage", "payment.record",
                    "feed.manage", "expense.manage", "finance.view", "user.manage", "audit.read",
                    "settings.manage"), "OWNER");
            SwingUtilities.invokeAndWait(() -> {
                LoginFrame login = new LoginFrame(client);
                MainFrame main = new MainFrame(client);
                try {
                    login.setVisible(true);
                    main.setVisible(true);
                    assertEquals(JFrame.EXIT_ON_CLOSE, login.getDefaultCloseOperation());
                    assertEquals(JFrame.EXIT_ON_CLOSE, main.getDefaultCloseOperation());
                    login.dispose();
                    assertTrue(main.isShowing());
                    JPanel content = field(main, "content");
                    Map<String, JPanel> cards = field(main, "cards");
                    assertEquals(13, cards.size());
                    for (var entry : cards.entrySet()) {
                        main.show(entry.getKey());
                        assertTrue(main.isShowing(), entry.getKey());
                        assertTrue(entry.getValue().isVisible(), entry.getKey());
                        assertEquals(1, java.util.Arrays.stream(content.getComponents())
                                .filter(Component::isVisible).count(), entry.getKey());
                    }
                } finally {
                    login.dispose();
                    main.dispose();
                }
            });
            // Flush queued WINDOW_CLOSED events; these must never call System.exit.
            SwingUtilities.invokeAndWait(() -> {});
        } finally {
            server.stop(0);
        }
    }

    @Test
    void roleWithoutDashboardStartsAtItsFirstAllowedView() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Requires a desktop display");
        ApiClient client = client(null, List.of("finance.view"), "STAFF");
        SwingUtilities.invokeAndWait(() -> {
            MainFrame main = new MainFrame(client);
            try {
                Map<String, JPanel> cards = field(main, "cards");
                assertEquals(java.util.Set.of("reports"), cards.keySet());
                assertEquals("reports", field(main, "active"));
                assertTrue(cards.get("reports").isVisible());
            } finally {
                main.dispose();
            }
        });
    }

    private static ApiClient client(HttpServer server, List<String> permissions, String role) throws Exception {
        ApiClient client = new ApiClient("http://127.0.0.1:" + (server == null ? 1 : server.getAddress().getPort()));
        Field user = ApiClient.class.getDeclaredField("user");
        user.setAccessible(true);
        user.set(client, Map.of("role", role, "fullName", "Test", "permissions", permissions));
        return client;
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
