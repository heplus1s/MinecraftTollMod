package dev.jdguo.toll;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TollPolicyTest {
    @Test void centeredInsideSingleChunk() {
        assertEquals(Set.of(new TollPolicy.Chunk(0, 0)), TollPolicy.coveredChunks(8, 8, 1));
    }
    @Test void exactEdgesCountAdjacentChunksButNotCorners() {
        assertEquals(Set.of(new TollPolicy.Chunk(0, 0), new TollPolicy.Chunk(-1, 0),
                new TollPolicy.Chunk(1, 0), new TollPolicy.Chunk(0, -1), new TollPolicy.Chunk(0, 1)),
                TollPolicy.coveredChunks(8, 8, 8));
    }
    @Test void negativeCoordinatesUseFloorNotTruncation() {
        assertEquals(Set.of(new TollPolicy.Chunk(-1, -1)), TollPolicy.coveredChunks(-8, -8, 1));
        assertEquals(Set.of(new TollPolicy.Chunk(-1, -1), new TollPolicy.Chunk(-1, 0),
                new TollPolicy.Chunk(0, -1), new TollPolicy.Chunk(0, 0)), TollPolicy.coveredChunks(0, 0, 1));
    }
    @Test void circleDoesNotUseBoundingSquare() {
        assertFalse(TollPolicy.coveredChunks(8, 8, 9).contains(new TollPolicy.Chunk(1, 1)));
        assertTrue(TollPolicy.coveredChunks(8, 8, 12).contains(new TollPolicy.Chunk(1, 1)));
    }
    @Test void ownershipRequiresEveryTouchedChunkAndFailsClosed() {
        UUID owner = UUID.randomUUID();
        assertTrue(TollPolicy.whollyOwned(owner, 8, 8, 8, c -> owner));
        assertFalse(TollPolicy.whollyOwned(owner, 8, 8, 8, c -> c.x() == -1 ? null : owner));
        assertFalse(TollPolicy.whollyOwned(owner, 8, 8, 8, c -> UUID.randomUUID()));
        assertFalse(TollPolicy.whollyOwned(owner, 8, 8, 8, c -> { throw new IllegalStateException(); }));
    }
    @Test void invalidRadiusAndPositionsRejected() {
        for (double radius : new double[]{0, .9, 128.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> TollPolicy.coveredChunks(0, 0, radius));
        assertThrows(IllegalArgumentException.class, () -> TollPolicy.coveredChunks(Double.NaN, 0, 2));
        assertTrue(TollPolicy.coveredChunks(0, 0, 128).size() < 324);
    }
    @Test void preciseMoneyAndPhysicalIntegers() {
        assertEquals(new BigDecimal("0.0001"), TollPolicy.parseAmount("0.0001", false));
        assertEquals(0, BigDecimal.valueOf(100).compareTo(TollPolicy.parseAmount("100.0000", true)));
        assertThrows(IllegalArgumentException.class, () -> TollPolicy.parseAmount("1.5", true));
    }
    @Test void invalidMoneyRejected() {
        for (String text : new String[]{"0", "-1", "+1", "1e2", "NaN", "1.00000", "", " 1", ".5", "1."})
            assertThrows(IllegalArgumentException.class, () -> TollPolicy.parseAmount(text, false));
        assertThrows(IllegalArgumentException.class, () -> TollPolicy.parseAmount(null, false));
    }
}
