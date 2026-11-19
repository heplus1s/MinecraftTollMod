package dev.jdguo.toll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class TollCreatorTest {
 TollStore.Point point(String mode){var p=new TollStore.Point();p.mode=mode;p.name=p.id;p.dimension="minecraft:overworld";p.currency="note";p.currencyName="央行券";p.amount="3";p.radius=3;p.owner=UUID.randomUUID().toString();p.recipient="system".equals(mode)?null:p.owner;return p;}
 @Test void systemCreatorPaysLikeOtherPlayers(){var p=point("system");p.creator=p.owner;assertFalse(TollPolicy.exemptFromNewOrder(p,p.creator));assertFalse(TollPolicy.exemptFromNewOrder(p,UUID.randomUUID().toString()));}
 @Test void privateCreatorExemptAndOtherPassengersStillPay(){var p=point("private");p.creator=p.owner;assertTrue(TollPolicy.exemptFromNewOrder(p,p.creator));assertFalse(TollPolicy.exemptFromNewOrder(p,UUID.randomUUID().toString()));}
 @Test void noCreatorDoesNotExemptArbitraryAdmins(){var p=point("system");p.owner=null;assertFalse(TollPolicy.exemptFromNewOrder(p,UUID.randomUUID().toString()));assertFalse(TollPolicy.isCreator(null,UUID.randomUUID().toString()));}
 @Test void knownOldAutomaticCreatorAndPendingOrderMigrate()throws Exception{
  var s=new TollStore();var p=point("system");s.points.put(p.name,p);var o=new TollStore.Order();o.mode="system";o.player=p.owner;o.point=p.id;o.currency=p.currency;o.amount=p.amount;s.orders.put(o.reference,o);
  var loaded=TollStore.fromJson(s.toJson());assertEquals(p.owner,loaded.points.get(p.name).creator);assertEquals(p.owner,loaded.orders.get(o.reference).creator);
  assertTrue(TollPolicy.isCreator(loaded.orders.get(o.reference).creator,o.player));
 }
 @Test void oldLegacyPayeeIsNotInventedAsCreator()throws Exception{
  var s=new TollStore();var p=point("legacy");p.owner=null;s.points.put(p.name,p);var loaded=TollStore.fromJson(s.toJson()).points.get(p.name);assertNull(loaded.creator);
 }
 @Test void explicitCreatorSurvivesRoundTripAndDoesNotFollowOwner()throws Exception{
  var s=new TollStore();var p=point("system");p.creator=UUID.randomUUID().toString();s.points.put(p.name,p);var loaded=TollStore.fromJson(s.toJson()).points.get(p.name);assertEquals(p.creator,loaded.creator);assertFalse(TollPolicy.exemptFromNewOrder(loaded,p.owner));
 }
 @Test void oldSystemCreatorOrderIsNotReplayedButNewOrderIsCharged()throws Exception{
  var s=new TollStore();var p=point("system");s.points.put(p.name,p);var o=new TollStore.Order();o.mode="system";o.creator=p.owner;o.player=p.owner;o.point=p.id;o.currency=p.currency;o.amount=p.amount;s.orders.put(o.reference,o);
  assertTrue(TollPolicy.exemptOrder(TollStore.fromJson(s.toJson()).orders.get(o.reference)));
  o.systemCreatorChargeAllowed=true;
  assertFalse(TollPolicy.exemptOrder(TollStore.fromJson(s.toJson()).orders.get(o.reference)));
  o.mode="private";o.recipient=p.owner;assertTrue(TollPolicy.exemptOrder(o));
 }
 @Test void privateExemptionFollowsBuilderNotTheOwner(){
  var p=point("private");p.creator=UUID.randomUUID().toString();
  assertFalse(TollPolicy.exemptFromNewOrder(p,p.owner));
  assertTrue(TollPolicy.exemptFromNewOrder(p,p.creator));
  var o=new TollStore.Order();o.mode="private";o.recipient=p.owner;o.creator=p.creator;
  o.player=p.creator;assertTrue(TollPolicy.exemptOrder(o));
  o.player=p.owner;assertFalse(TollPolicy.exemptOrder(o));
 }
 @Test void malformedCreatorFailsClosed(){var s=new TollStore();var p=point("system");p.creator="not-a-player-uuid";s.points.put(p.name,p);assertThrows(java.io.IOException.class,()->TollStore.fromJson(s.toJson()));}
}
