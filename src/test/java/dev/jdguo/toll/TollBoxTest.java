package dev.jdguo.toll;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TollBoxTest {
    private TollStore.Point box(double x,double y,double z) {
        var p=new TollStore.Point();
        p.name="point";p.dimension="minecraft:overworld";p.mode="system";
        p.x=x;p.y=y;p.z=z;p.radius=2;p.halfZ=5d;p.above=2d;p.below=1d;
        p.currency="note";p.currencyName="央行券";p.amount="10";
        return p;
    }
    private Geometry.Position pos(double x,double y,double z){return new Geometry.Position(x,y,z);}
    @Test void detectsFastThroughPassAndRejectsParallelAndStackedTracks() {
        var p=box(0,0,0);
        assertTrue(p.crosses(pos(-30,0,5),pos(30,0,5)));
        assertFalse(p.crosses(pos(-30,0,5.01),pos(30,0,5.01)));
        assertFalse(p.crosses(pos(-30,2.01,5),pos(30,2.01,5)));
        assertFalse(p.crosses(pos(-30,-1.01,5),pos(30,-1.01,5)));
    }
    @Test void boxBufferContainsExtendedCorners() {
        var p=box(0,0,0);
        assertTrue(p.inside(pos(2,2,5),0));
        assertFalse(p.inside(pos(4,4,7),0));
        assertTrue(p.inside(pos(4,4,7),2));
        assertFalse(p.inside(pos(4.01,4,7),2));
    }
    @Test void overlapIncludesBufferAndMixedCornersButNotDistantLanes() {
        var p=box(0,0,0);
        assertTrue(Geometry.overlaps(p,box(8,0,0),2));
        assertFalse(Geometry.overlaps(p,box(8.01,0,0),2));
        assertFalse(Geometry.overlaps(p,box(0,7.01,0),2));
        var circle=box(4.5,0,7.5);circle.halfZ=null;circle.radius=1;
        assertFalse(Geometry.overlaps(p,circle,0));
        assertTrue(Geometry.overlaps(p,circle,2));
        assertTrue(Geometry.overlaps(circle,p,2));
    }
    @Test void privateCoverageIncludesRectangularCornerAndExactBoundary() {
        var p=box(14,0,11);
        assertTrue(TollPolicy.coveredChunks(p).contains(new TollPolicy.Chunk(1,1)));
        assertFalse(TollPolicy.coveredChunks(p.x,p.z,p.radius).contains(new TollPolicy.Chunk(1,1)));
    }
    @Test void persistenceKeepsRectangleAndMissingShapeStaysRound() throws Exception {
        var p=box(0,0,0);var s=new TollStore();s.points.put(p.name,p);
        assertEquals(5d,TollStore.fromJson(s.toJson()).points.get(p.name).halfZ);
        p.halfZ=null;
        var old=TollStore.fromJson(s.toJson()).points.get(p.name);
        assertFalse(old.isBox()); assertFalse(old.inside(pos(2,0,2),0));
        p.halfZ=129d;
        assertThrows(java.io.IOException.class,()->TollStore.fromJson(s.toJson()));
    }
}
