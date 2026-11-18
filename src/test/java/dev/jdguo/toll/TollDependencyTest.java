package dev.jdguo.toll;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TollDependencyTest {
    @Test void actualFabricPredicateRejectsOldBankAndAcceptsCandidate() throws Exception {
        var metadata=JsonParser.parseString(Files.readString(Path.of("src/main/resources/fabric.mod.json"))).getAsJsonObject();
        var depends=metadata.getAsJsonObject("depends");
        var bank=VersionPredicate.parse(depends.get("bankmod").getAsString());
        assertFalse(bank.test(Version.parse("1.0.16")));
        assertTrue(bank.test(Version.parse("1.0.17")));
        assertTrue(bank.test(Version.parse("1.0.18")));
        assertEquals("1.20.1",depends.get("minecraft").getAsString());
        assertEquals(">=0.15.0",depends.get("fabricloader").getAsString());
        assertEquals(">=17",depends.get("java").getAsString());
    }
}
