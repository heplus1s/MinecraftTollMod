package dev.jdguo.toll;
import java.nio.file.*;
import java.util.*;
public class CoreSmoke {
 static void check(boolean x){if(!x)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  var c=new Geometry.Position(0,0,0);
  check(Geometry.crosses(new Geometry.Position(-20,0,0),new Geometry.Position(20,0,0),c,2,4));
  check(!Geometry.crosses(new Geometry.Position(-20,8,0),new Geometry.Position(20,8,0),c,2,4));
  check(Geometry.crosses(new Geometry.Position(0,-8,0),new Geometry.Position(0,8,0),c,2,4));
  check(!Geometry.crosses(new Geometry.Position(-20,0,3),new Geometry.Position(20,0,3),c,2,4));
  check(!Geometry.crosses(new Geometry.Position(-20,0,0),new Geometry.Position(20,100,0),c,2,4));
  var all=List.of(new BankBridge.Currency("note","银行券"),new BankBridge.Currency("other","银行券"));
  try{BankBridge.resolve("银行券",all);throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(BankBridge.resolve("id:note",all).id().equals("note"));
  try{BankBridge.resolve("中央银行券",all);throw new AssertionError();}catch(IllegalArgumentException expected){}
  var dir=Files.createTempDirectory("toll-core-");var path=dir.resolve("data/state.json");
  var s=new TollStore();var o=new TollStore.Order();o.player=UUID.randomUUID().toString();o.recipient=UUID.randomUUID().toString();o.point=UUID.randomUUID().toString();o.currency="note";o.amount="3";s.orders.put(o.reference,o);s.blocked.add("player:point");s.save(path);var read=TollStore.load(path);check(read.orders.containsKey(o.reference)&&read.blocked.equals(s.blocked));
  Files.writeString(path,"broken");try{TollStore.load(path);throw new AssertionError();}catch(java.io.IOException expected){}check(Files.readString(path).equals("broken"));
  System.out.println("PASS: 10 core checks (geometry, currency, persistence); not Fabric runtime tests");
 }
}
