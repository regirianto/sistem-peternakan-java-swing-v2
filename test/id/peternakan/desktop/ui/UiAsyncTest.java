package id.peternakan.desktop.ui;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiAsyncTest {
    @Test
    void runsFinishedCallbackWhenBackgroundWorkFails() {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean finished = new AtomicBoolean();

        Ui.completeAsync(
                () -> {
                    throw new IllegalStateException("gagal");
                },
                ignored -> {
                },
                failure::set,
                () -> finished.set(true));

        assertInstanceOf(IllegalStateException.class, failure.get());
        assertTrue(finished.get());
    }
}
