package dev.jdguo.bank.api;
import java.math.BigDecimal;
import java.util.*;
/** Test double intentionally uses a different namespace, reproducing upgrade hazard. */
public final class TollSettlement {
 public record Result(boolean success,String code) {}
 public static final Set<String> receipts=new HashSet<>();
 public static int calls;
 public static long revision;
 public static String payload;
 public static boolean failSave;
 public static String loadState(){var row=new com.google.gson.JsonObject();row.addProperty("success",true);row.addProperty("revision",revision);row.addProperty("payload",payload);return row.toString();}
 public static Result saveState(long expected,String state){if(failSave)return new Result(false,"save_failed");if(expected!=revision)return new Result(false,"state_conflict");payload=state;revision++;return new Result(true,"ok");}
 public static String currencies(){return "[]";}
 public static Result transfer(UUID from,UUID to,String currency,BigDecimal amount,String ref){calls++;receipts.add("toll:"+ref);return new Result(true,"ok");}
 public static Result collectSystem(UUID from,String currency,BigDecimal amount,String ref){return transfer(from,null,currency,amount,ref);}
}
