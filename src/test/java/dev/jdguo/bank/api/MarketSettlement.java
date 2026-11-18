package dev.jdguo.bank.api;
import java.math.BigInteger;
import java.util.*;
/** Test double with the ORIGINAL namespace, distinct from the modern namespace. */
public final class MarketSettlement {
 public record Result(boolean success,String code) {}
 public static final Set<String> receipts=new HashSet<>();
 public static int calls;
 public static Result transfer(UUID from,UUID to,String currency,BigInteger amount,String ref){calls++;receipts.add(ref);return new Result(true,"ok");}
}
