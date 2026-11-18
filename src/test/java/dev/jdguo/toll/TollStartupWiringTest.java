package dev.jdguo.toll;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Structural lifecycle regression, not a live Fabric startup test. */
class TollStartupWiringTest {
    private String source() throws Exception {
        return Files.readString(Path.of("src/main/java/dev/jdguo/toll/TollMod.java"));
    }
    @Test void startupOnlySchedulesInitialization() throws Exception {
        String source = source();
        String started = source.substring(source.indexOf("SERVER_STARTED.register"), source.indexOf("SERVER_STOPPING.register"));
        assertTrue(started.contains("initializeOnNextTick=true"));
        assertFalse(started.contains("TollStateRepository.open"));
    }
    @Test void firstTickConsumesAttemptBeforeTouchingPersistentState() throws Exception {
        String source = source();
        String tick = source.substring(source.indexOf("private void tick("));
        assertTrue(tick.indexOf("initializeOnNextTick=false") < tick.indexOf("TollStateRepository.open"));
        assertTrue(tick.indexOf("TollStateRepository.open") < tick.indexOf("if(!healthy)return"));
    }
    @Test void stoppingCancelsPendingInitialization() throws Exception {
        String source = source();
        String stopped = source.substring(source.indexOf("SERVER_STOPPED.register"), source.indexOf("ServerTickEvents.END_SERVER_TICK"));
        assertTrue(stopped.contains("initializeOnNextTick=false"));
    }
}
