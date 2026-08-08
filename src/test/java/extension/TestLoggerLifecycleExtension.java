package extension;

import java.util.Optional;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestLoggerLifecycleExtension implements BeforeEachCallback, TestWatcher {

    @Override
    public void beforeEach(ExtensionContext context) {
        log.info("[TEST] Starting: {}", context.getDisplayName());
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        log.info("[TEST] PASSED: {}", context.getDisplayName());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        log.error("[TEST] FAILED: {}", context.getDisplayName(), cause);
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        log.warn("[TEST] ABORTED: {}", context.getDisplayName(), cause);
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        log.info("[TEST] DISABLED: {} ({})",
                context.getDisplayName(),
                reason.orElse("no reason"));
    }
}