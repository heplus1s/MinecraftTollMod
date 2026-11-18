package dev.jdguo.toll;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class TollStateRepositoryTest {
 @TempDir Path dir;
 @BeforeEach void reset(){dev.jdguo.bank.api.TollSettlement.revision=0;dev.jdguo.bank.api.TollSettlement.payload=null;dev.jdguo.bank.api.TollSettlement.failSave=false;}
 @Test void legacyMigratedOnceThenBankAlwaysWins()throws Exception{
  var file=dir.resolve("state.json");var legacy=new TollStore();legacy.blocked.add("old:point");legacy.save(file);
  var repo=TollStateRepository.open(file);assertTrue(repo.state.blocked.contains("old:point"));assertEquals(1,dev.jdguo.bank.api.TollSettlement.revision);
  repo.state.blocked.add("bank:point");repo.save();Files.writeString(file,"corrupt stale local mirror");
  var restored=TollStateRepository.open(file);assertTrue(restored.state.blocked.contains("bank:point"));assertEquals(2,dev.jdguo.bank.api.TollSettlement.revision);
 }
 @Test void oldJarCannotLoadMigratedLocalFile()throws Exception{
  var file=dir.resolve("state.json");var original=new TollStore();original.blocked.add("original:point");original.save(file);
  TollStateRepository.open(file);assertThrows(IOException.class,()->TollStore.load(file));
  assertTrue(TollStore.load(file.resolveSibling("state.json.pre-bank-migration")).blocked.contains("original:point"));
 }
 @Test void casConflictFreezesStaleSession()throws Exception{
  var a=TollStateRepository.open(dir.resolve("missing.json"));var b=TollStateRepository.open(dir.resolve("missing.json"));a.state.blocked.add("a:point");a.save();
  b.state.blocked.add("b:point");assertThrows(IOException.class,b::save);assertThrows(IOException.class,b::save);
  var restored=TollStateRepository.open(dir.resolve("missing.json"));assertTrue(restored.state.blocked.contains("a:point"));assertFalse(restored.state.blocked.contains("b:point"));
 }
 @Test void failedMigrationDoesNotFallbackOrReturnUsableState()throws Exception{
  var file=dir.resolve("legacy.json");new TollStore().save(file);dev.jdguo.bank.api.TollSettlement.failSave=true;
  assertThrows(IOException.class,()->TollStateRepository.open(file));assertNull(dev.jdguo.bank.api.TollSettlement.payload);
 }
 @Test void corruptedBankStateDoesNotFallbackToValidLegacyFile()throws Exception{
  var file=dir.resolve("legacy.json");new TollStore().save(file);dev.jdguo.bank.api.TollSettlement.payload="broken";
  assertThrows(IOException.class,()->TollStateRepository.open(file));
 }
}
