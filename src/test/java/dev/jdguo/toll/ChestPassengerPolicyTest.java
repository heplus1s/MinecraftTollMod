package dev.jdguo.toll;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChestPassengerPolicyTest {
    @Test void onlyDefiniteInsufficientPassengerIsEjected() {
        assertTrue(TollPolicy.shouldEjectChestPassenger("INSUFFICIENT",false,true));
        for(String state:new String[]{"PENDING","PAID","REVIEW","CONFISCATED",null})
            assertFalse(TollPolicy.shouldEjectChestPassenger(state,false,true));
    }
    @Test void creatorAndDepartedRiderAreNotEjected() {
        assertFalse(TollPolicy.shouldEjectChestPassenger("INSUFFICIENT",true,true));
        assertFalse(TollPolicy.shouldEjectChestPassenger("INSUFFICIENT",false,false));
    }
    @Test void completedEjectionCannotReplay() {
        assertFalse(TollPolicy.shouldEjectChestPassenger("REVIEW",false,true));
    }
    /** Source wiring assertion only; real game dismount physics are not simulated. */
    @Test void directDismountDoesNotTouchBoatOrRecharge() throws Exception {
        String source=Files.readString(Path.of("src/main/java/dev/jdguo/toll/TollMod.java"));
        String action=source.substring(source.indexOf("private void ejectUnpaidPassenger("),source.indexOf("private void confiscate("));
        assertTrue(action.contains("debtor.stopRiding()"));
        assertTrue(action.contains("seizureWindows.remove(order.reference)"));
        assertTrue(action.contains("order.status=\"REVIEW\""));
        assertFalse(action.contains("discard("));
        assertFalse(action.contains("requestTeleport("));
        assertFalse(action.contains("BankBridge.charge"));
        assertFalse(action.contains("getInventory"));
        assertTrue(source.contains("TollPolicy.mayConfiscateBoat(boat instanceof ChestBoatEntity,states)"));
    }
}
