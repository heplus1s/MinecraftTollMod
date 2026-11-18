package dev.jdguo.toll;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TollStateTest {
    @TempDir Path dir;
    private TollStore.Point point(String mode) {
        var p=new TollStore.Point();p.name="中文收费点";p.dimension="minecraft:overworld";
        p.currency="note";p.currencyName="央行券";p.amount="1.2500";
        p.radius=3;p.mode=mode;p.owner=UUID.randomUUID().toString();
        p.recipient="system".equals(mode)?null:p.owner;p.below=1.0;p.above=3.0;
        return p;
    }
    private TollStore state(String mode) {
        var s=new TollStore();var p=point(mode);s.points.put(p.name,p);
        var o=new TollStore.Order();o.mode=mode;o.player=UUID.randomUUID().toString();
        o.point=p.id;o.recipient=p.recipient;o.currency=p.currency;o.amount=p.amount;
        s.orders.put(o.reference,o);s.blocked.add(o.player+":"+p.id);return s;
    }
    @Test void systemRoundTripHasNoPlayerRecipient() throws Exception {
        var s=state("system");var file=dir.resolve("state.json");s.save(file);
        var loaded=TollStore.load(file);var p=loaded.points.values().iterator().next();
        assertEquals("system",p.mode);assertNull(p.recipient);
        assertEquals("中文收费点",p.name);assertEquals("央行券",p.currencyName);
        assertEquals(1.0,p.below());assertEquals(3.0,p.above());
        var o=loaded.orders.values().iterator().next();assertEquals("system",o.mode);
        assertNull(o.recipient);assertEquals("PENDING",o.status);assertEquals("1.2500",o.amount);
        assertEquals(s.blocked,loaded.blocked);
    }
    @Test void privateRoundTripPreservesOwnerAndReference() throws Exception {
        var s=state("private");var file=dir.resolve("state.json");s.save(file);
        var loaded=TollStore.load(file);var p=loaded.points.values().iterator().next();
        assertEquals("private",p.mode);assertEquals(p.owner,p.recipient);
        assertEquals(s.orders.keySet(),loaded.orders.keySet());
        assertEquals(p.owner,loaded.orders.values().iterator().next().recipient);
    }
    @Test void legacySchemaOneWithoutNewFieldsStillLoads() throws Exception {
        var s=state("legacy");var file=dir.resolve("state.json");s.save(file);
        var json=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        for(var value:json.getAsJsonObject("points").entrySet()) {
            var p=value.getValue().getAsJsonObject();p.remove("mode");p.remove("owner");p.remove("below");p.remove("above");
        }
        for(var value:json.getAsJsonObject("orders").entrySet())value.getValue().getAsJsonObject().remove("mode");
        Files.writeString(file,json.toString());var loaded=TollStore.load(file);
        var p=loaded.points.values().iterator().next();assertEquals("legacy",p.mode);
        assertNull(p.owner);assertEquals(4.0,p.below());assertEquals(4.0,p.above());
        assertEquals("legacy",loaded.orders.values().iterator().next().mode);
    }
    @Test void privateRecipientCannotDifferFromOwner() throws Exception {
        var s=state("private");s.points.values().iterator().next().recipient=UUID.randomUUID().toString();
        var file=dir.resolve("state.json");s.save(file);assertThrows(IOException.class,()->TollStore.load(file));
    }
    @Test void invalidModeAndFutureSchemaFailClosedWithoutReplacingFile() throws Exception {
        var s=state("system");var file=dir.resolve("state.json");s.points.values().iterator().next().mode="anything";
        s.save(file);String original=Files.readString(file);assertThrows(IOException.class,()->TollStore.load(file));
        assertEquals(original,Files.readString(file));s=state("system");s.schema=2;s.save(file);
        assertThrows(IOException.class,()->TollStore.load(file));
    }
    @Test void reviewAndSeizurePersistenceRemainDistinct() throws Exception {
        var s=state("system");var o=s.orders.values().iterator().next();o.status="REVIEW";o.lastCode="bridge_unknown";
        var z=new TollStore.Seizure();z.boat=UUID.randomUUID().toString();z.dimension="minecraft:overworld";
        z.entityType="minecraft:boat";z.nbt="{}";z.reason=o.reference;s.seizures.put(z.boat,z);
        var file=dir.resolve("state.json");s.save(file);var loaded=TollStore.load(file);
        assertEquals("REVIEW",loaded.orders.get(o.reference).status);
        assertEquals("ARCHIVED",loaded.seizures.get(z.boat).status);
    }
}
