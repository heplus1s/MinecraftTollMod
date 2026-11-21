package dev.jdguo.toll;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TollNearbyTest {
    private static final String WORLD = "minecraft:overworld";

    private static TollStore.Point point(double x, double y, double z) {
        var p = new TollStore.Point();
        p.dimension = WORLD;
        p.x = x;
        p.y = y;
        p.z = z;
        p.radius = 1;
        p.height = 1;
        return p;
    }

    private static TollNearby.Selection at(double x, double y, double z, TollStore.Point... points) {
        return TollNearby.select(List.of(points), WORLD, new Geometry.Position(x, y, z));
    }

    @Test void occupiedBoxWinsEvenWhenItsCenterIsFartherThanTen() {
        var box = point(0, 0, 0);
        box.radius = 10;
        box.halfZ = 35.0;
        box.below = 1.0;
        box.above = 3.0;
        var closer = point(0, 7, 30);
        assertSame(box, at(0, 3, 30, closer, box).point());
        assertSame(closer, at(0, 3.01, 30, box, closer).point());
    }

    @Test void usesActualThreeDimensionalMidpointAndIncludesExactlyTen() {
        var asymmetric = point(6, 6, 0);
        asymmetric.below = 1.0;
        asymmetric.above = 5.0;
        assertSame(asymmetric, at(0, 0, 0, asymmetric).point());
        assertNull(at(-0.01, 0, 0, asymmetric).point());
    }

    @Test void ignoresOtherDimensionsAndSelectsOnlyNearest() {
        var otherDimension = point(0, 0, 0);
        otherDimension.dimension = "minecraft:the_nether";
        var far = point(8, 0, 0);
        var near = point(4, 0, 0);
        assertSame(near, at(0, 0, 0, far, otherDimension, near).point());
    }

    @Test void equalCentersRequireExplicitId() {
        var result = at(0, 0, 0, point(-4, 0, 0), point(4, 0, 0));
        assertTrue(result.ambiguous());
        assertNull(result.point());
    }

    @Test void doesNotTreatHorizontalBufferAsOccupied() {
        var cylinder = point(11, 0, 0);
        cylinder.radius = 10;
        assertNull(at(0, 0, 0, cylinder).point());
        assertSame(cylinder, at(1, 0, 0, cylinder).point());
    }
}
