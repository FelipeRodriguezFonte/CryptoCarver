package com.cryptocarver.ui;

import com.cryptocarver.model.OperationResult;
import com.cryptocarver.model.OperationDetail;
import com.cryptocarver.model.SecretVisibilityProfile;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

class ResultCaptureCoordinatorTest {
    @Test void constructionDoesNotReadProvidersAndEachCaptureUsesLiveState() {
        AtomicReference<OperationResult> published = new AtomicReference<>();
        AtomicReference<SecretVisibilityProfile> profile = new AtomicReference<>(SecretVisibilityProfile.MASKED);
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        Supplier<OperationResult> liveResult = () -> {
            assertTrue(ready.get(), "Constructor must not resolve live providers");
            return published.get();
        };
        ResultCaptureCoordinator capture = new ResultCaptureCoordinator(
                () -> null, () -> null, () -> null, () -> null,
                ResultAreaTracker::new, liveResult, () -> "fixture", () -> "fixture",
                profile::get, ignored -> {}, (title, message) -> {}, (area, selected) -> {});
        ready.set(true);
        published.set(OperationResult.forOperation("fixture")
                .output("invented secret".getBytes(StandardCharsets.UTF_8), OperationDetail.Classification.SECRET).build());
        assertEquals("***MASKED***", capture.resolveCurrentOutputText());
        profile.set(SecretVisibilityProfile.FULL_LAB);
        assertEquals("invented secret", capture.resolveCurrentOutputText());
        published.set(OperationResult.forOperation("replacement").output("replacement output".getBytes(StandardCharsets.UTF_8)).build());
        assertEquals("replacement output", capture.resolveCurrentOutputText());
    }
}
