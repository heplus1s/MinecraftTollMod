package dev.jdguo.toll;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TollTransportTest {
 @TempDir Path dir;
 @BeforeEach void reset(){dev.jdguo.bank.api.MarketSettlement.receipts.clear();dev.jdguo.bank.api.MarketSettlement.calls=0;dev.jdguo.bank.api.TollSettlement.receipts.clear();dev.jdguo.bank.api.TollSettlement.calls=0;}
 TollStore.Order pending(String transport){var o=new TollStore.Order();o.transport=transport;o.player=UUID.randomUUID().toString();o.recipient=UUID.randomUUID().toString();o.point=UUID.randomUUID().toString();o.currency="note";o.amount="3";return o;}
 @Test void legacyCrashThenUpgradeReplaysOriginalProtocol()throws Exception{
  var s=new TollStore();var o=pending("legacy");s.orders.put(o.reference,o);var file=dir.resolve("state.json");s.save(file);
  // Original bank accepted, but toll process crashed before saving PAID.
  assertTrue(BankBridge.charge(o).success());
  var recovered=TollStore.load(file).orders.get(o.reference);assertEquals("PENDING",recovered.status);
  assertTrue(BankBridge.modernApi());assertTrue(BankBridge.charge(recovered).success());
  assertEquals(2,dev.jdguo.bank.api.MarketSettlement.calls);assertEquals(1,dev.jdguo.bank.api.MarketSettlement.receipts.size());assertEquals(0,dev.jdguo.bank.api.TollSettlement.calls);
 }
 @Test void modernCrashReplayUsesSameNamespace()throws Exception{
  var s=new TollStore();var o=pending("toll-v1");s.orders.put(o.reference,o);var file=dir.resolve("state.json");s.save(file);
  assertTrue(BankBridge.charge(o).success());assertTrue(BankBridge.charge(TollStore.load(file).orders.get(o.reference)).success());
  assertEquals(1,dev.jdguo.bank.api.TollSettlement.receipts.size());assertEquals(0,dev.jdguo.bank.api.MarketSettlement.calls);
 }
 @Test void downgradeDoesNotFallBackToLegacy()throws Exception{
  URL[] urls={BankBridge.class.getProtectionDomain().getCodeSource().getLocation(),com.google.gson.Gson.class.getProtectionDomain().getCodeSource().getLocation()};
  try(var loader=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader()){
   @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
    if(name.equals("dev.jdguo.bank.api.TollSettlement"))throw new ClassNotFoundException("simulated older bank");return super.loadClass(name,resolve);
   }
  }){
   var orderClass=loader.loadClass("dev.jdguo.toll.TollStore$Order");Object order=orderClass.getConstructor().newInstance();orderClass.getField("transport").set(order,"toll-v1");
   var bridge=loader.loadClass("dev.jdguo.toll.BankBridge");Object result=bridge.getMethod("charge",orderClass).invoke(null,order);
   assertEquals(false,result.getClass().getMethod("success").invoke(result));assertEquals("required_transport_unavailable",result.getClass().getMethod("code").invoke(result));
   assertEquals(0,loader.loadClass("dev.jdguo.bank.api.MarketSettlement").getField("calls").get(null));
  }
 }
 @Test void oldSaveWithoutTransportDefaultsLegacy()throws Exception{
  var s=new TollStore();var o=pending("legacy");s.orders.put(o.reference,o);var file=dir.resolve("state.json");s.save(file);
  var json=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();json.getAsJsonObject("orders").getAsJsonObject(o.reference).remove("transport");Files.writeString(file,json.toString());
  assertEquals("legacy",TollStore.load(file).orders.get(o.reference).transport);
 }
}
