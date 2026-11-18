package dev.jdguo.toll;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BoatSettlementPolicyTest {
 @Test void ordinaryBoatRequiresEveryRiderDefinitelyInsufficient(){
  assertTrue(TollPolicy.mayConfiscateBoat(false,List.of("INSUFFICIENT")));
  assertTrue(TollPolicy.mayConfiscateBoat(false,List.of("INSUFFICIENT","INSUFFICIENT")));
  for(String other:List.of("PAID","EXEMPT","PENDING","REVIEW","UNKNOWN")){
   assertFalse(TollPolicy.mayConfiscateBoat(false,List.of("INSUFFICIENT",other)));
   assertFalse(TollPolicy.mayConfiscateBoat(false,List.of(other,"INSUFFICIENT")));
  }
 }
 @Test void chestBoatNeverConfiscatedEvenIfEveryoneOwes(){
  assertFalse(TollPolicy.mayConfiscateBoat(true,List.of("INSUFFICIENT")));
  assertFalse(TollPolicy.mayConfiscateBoat(true,List.of("INSUFFICIENT","INSUFFICIENT")));
 }
 @Test void missingBatchOrPassengerNeverImpliesAllOwe(){
  assertFalse(TollPolicy.mayConfiscateBoat(false,List.of()));
  assertFalse(TollPolicy.mayConfiscateBoat(false,List.of("UNKNOWN")));
  assertFalse(TollPolicy.mayConfiscateBoat(false,null));
 }
}
