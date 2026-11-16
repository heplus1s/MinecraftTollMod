package dev.jdguo.toll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TollTest {
    Geometry.Position c=new Geometry.Position(0,0,0);
    @Test void fastPass(){assertTrue(Geometry.crosses(new Geometry.Position(-20,0,0),new Geometry.Position(20,0,0),c,2,4));}
    @Test void otherFloor(){assertFalse(Geometry.crosses(new Geometry.Position(-20,8,0),new Geometry.Position(20,8,0),c,2,4));}
    @Test void vertical(){assertTrue(Geometry.crosses(new Geometry.Position(0,-8,0),new Geometry.Position(0,8,0),c,2,4));}
    @Test void miss(){assertFalse(Geometry.crosses(new Geometry.Position(-20,0,3),new Geometry.Position(20,0,3),c,2,4));}
    @Test void diagonalDifferentIntervals(){assertFalse(Geometry.crosses(new Geometry.Position(-20,0,0),new Geometry.Position(20,100,0),c,2,4));}
    @Test void chineseAndAmbiguity(){var all=List.of(new BankBridge.Currency("note","银行券"),new BankBridge.Currency("other","银行券"));assertThrows(IllegalArgumentException.class,()->BankBridge.resolve("银行券",all));assertEquals("note",BankBridge.resolve("id:note",all).id());}
    @Test void unknownDoesNotGuess(){assertThrows(IllegalArgumentException.class,()->BankBridge.resolve("中央银行券",List.of(new BankBridge.Currency("note","银行券"))));}
    @Test void durableState(@TempDir Path dir)throws Exception{var s=new TollStore();var o=new TollStore.Order();o.player=UUID.randomUUID().toString();o.recipient=UUID.randomUUID().toString();o.point=UUID.randomUUID().toString();o.currency="note";o.amount="3";s.orders.put(o.reference,o);s.blocked.add("player:point");var p=dir.resolve("data/state.json");s.save(p);var read=TollStore.load(p);assertEquals(o.reference,read.orders.get(o.reference).reference);assertEquals(s.blocked,read.blocked);}
    @Test void invalidSaveNotOverwritten(@TempDir Path dir)throws Exception{var p=dir.resolve("state.json");Files.writeString(p,"broken");assertThrows(java.io.IOException.class,()->TollStore.load(p));assertEquals("broken",Files.readString(p));}
    @Test void malformedRecordRefusesStartup(@TempDir Path dir)throws Exception{var p=dir.resolve("state.json");Files.writeString(p,"{\"orders\":{\"broken\":null}}");assertThrows(java.io.IOException.class,()->TollStore.load(p));}
    @Test void bufferBoundary(){assertTrue(Geometry.inside(new Geometry.Position(4,6,0),c,4,6));assertFalse(Geometry.inside(new Geometry.Position(4.01,6,0),c,4,6));}
    @Test void stationaryOutside(){assertFalse(Geometry.crosses(new Geometry.Position(8,0,0),new Geometry.Position(8,0,0),c,2,4));}
}
