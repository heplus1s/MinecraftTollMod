package dev.jdguo.toll;
import java.lang.reflect.*;
import java.math.BigInteger;
import java.math.BigDecimal;
import com.google.gson.*;
import java.util.*;

/** No direct dependency on client classes or banking implementation at compile time. */
public final class BankBridge {
    public record Currency(String id,String name) {}
    public record Result(boolean success,String code) {}
    public static void verifyApi() throws ReflectiveOperationException {
        Class<?> api=Class.forName("dev.jdguo.bank.api.MarketSettlement");
        Method transfer=api.getMethod("transfer",UUID.class,UUID.class,String.class,BigInteger.class,String.class);
        Class<?> result=transfer.getReturnType();
        if(result.getMethod("success").getReturnType()!=boolean.class||result.getMethod("code").getReturnType()!=String.class)throw new NoSuchMethodException("Incompatible bank TxResult");
        Class<?> runtime=Class.forName("dev.jdguo.bank.persistence.BankRuntime");runtime.getField("state");runtime.getMethod("books");
    }
    public static boolean modernApi() {
        try {
            var api=Class.forName("dev.jdguo.bank.api.TollSettlement");
            api.getMethod("currencies");
            api.getMethod("transfer",UUID.class,UUID.class,String.class,BigDecimal.class,String.class);
            api.getMethod("collectSystem",UUID.class,String.class,BigDecimal.class,String.class);
            return true;
        } catch(ReflectiveOperationException e){return false;}
    }
    public static List<Currency> currencies() throws ReflectiveOperationException {
        if(modernApi()) {
            String json=(String)Class.forName("dev.jdguo.bank.api.TollSettlement").getMethod("currencies").invoke(null);
            List<Currency> values=new ArrayList<>();
            for(var entry:JsonParser.parseString(json).getAsJsonArray()) {
                var row=entry.getAsJsonObject();values.add(new Currency(row.get("id").getAsString(),row.get("name").getAsString()));
            }
            return values;
        }
        Class<?> runtime=Class.forName("dev.jdguo.bank.persistence.BankRuntime");
        if(runtime.getField("state").get(null)==null)throw new IllegalStateException("银行账本尚未加载");
        Object books=runtime.getMethod("books").invoke(null);
        Collection<?> defs=(Collection<?>)books.getClass().getMethod("currencies").invoke(books);
        List<Currency> result=new ArrayList<>();
        for(Object d:defs)result.add(new Currency((String)d.getClass().getMethod("id").invoke(d),(String)d.getClass().getMethod("name").invoke(d)));
        return result;
    }
    public static Currency resolve(String text,List<Currency> all) {
        if(text.startsWith("id:")) return all.stream().filter(c->c.id.equals(text.substring(3))).findFirst().orElseThrow(()->new IllegalArgumentException("未知货币 ID"));
        List<Currency> matches=all.stream().filter(c->c.id.equals(text)||c.name.equals(text)).toList();
        if(matches.size()!=1)throw new IllegalArgumentException(matches.isEmpty()?"未知/不支持的币种":"币种重名，请使用 id:货币ID："+matches.stream().map(c->c.id).toList());
        return matches.get(0);
    }
    public record State(long revision,String payload) {}
    public static boolean stateApi(){
        try {var api=Class.forName("dev.jdguo.bank.api.TollSettlement");api.getMethod("loadState");api.getMethod("saveState",long.class,String.class);return true;}
        catch(ReflectiveOperationException missing){return false;}
    }
    public static State loadState()throws ReflectiveOperationException {
        String json=(String)Class.forName("dev.jdguo.bank.api.TollSettlement").getMethod("loadState").invoke(null);
        var state=JsonParser.parseString(json).getAsJsonObject();
        if(!state.has("success")||!state.get("success").getAsBoolean()||!state.has("revision"))throw new IllegalStateException("Bank toll state unavailable");
        return new State(state.get("revision").getAsLong(),state.get("payload")==null||state.get("payload").isJsonNull()?null:state.get("payload").getAsString());
    }
    public static Result saveState(long expectedRevision,String payload){
        try {
            Object r=Class.forName("dev.jdguo.bank.api.TollSettlement").getMethod("saveState",long.class,String.class).invoke(null,expectedRevision,payload);
            return new Result((boolean)r.getClass().getMethod("success").invoke(r),(String)r.getClass().getMethod("code").invoke(r));
        }catch(ReflectiveOperationException|RuntimeException unavailable){return new Result(false,"state_unknown");}
    }
    public static Result previewSystem(String currency,BigDecimal amount) {
        try {
            var api=Class.forName("dev.jdguo.bank.api.TollSettlement");
            Object r=api.getMethod("previewSystem",String.class,BigDecimal.class).invoke(null,currency,amount);
            return new Result((boolean)r.getClass().getMethod("success").invoke(r),(String)r.getClass().getMethod("code").invoke(r));
        }catch(ReflectiveOperationException|RuntimeException unavailable){return new Result(false,"preview_unavailable");}
    }
    public static Result charge(TollStore.Order order) {
        if ("toll-v1".equals(order.transport)) {
            if(!modernApi())return new Result(false,"required_transport_unavailable");
            try {
                var api=Class.forName("dev.jdguo.bank.api.TollSettlement");
                Object r="system".equals(order.mode)
                    ? api.getMethod("collectSystem",UUID.class,String.class,BigDecimal.class,String.class).invoke(null,UUID.fromString(order.player),order.currency,new BigDecimal(order.amount),order.reference)
                    : api.getMethod("transfer",UUID.class,UUID.class,String.class,BigDecimal.class,String.class).invoke(null,UUID.fromString(order.player),UUID.fromString(order.recipient),order.currency,new BigDecimal(order.amount),order.reference);
                return new Result((boolean)r.getClass().getMethod("success").invoke(r),(String)r.getClass().getMethod("code").invoke(r));
            }catch(ReflectiveOperationException|RuntimeException unknown){return new Result(false,"bridge_unknown");}
        }
        if(!"legacy".equals(order.transport))return new Result(false,"unknown_transport");
        if("system".equals(order.mode))return new Result(false,"unsupported_system");
        try {new BigDecimal(order.amount).toBigIntegerExact();}catch(ArithmeticException e){return new Result(false,"unsupported_precision");}
        return transfer(UUID.fromString(order.player),UUID.fromString(order.recipient),order.currency,new BigDecimal(order.amount).toBigIntegerExact().toString(),order.reference);
    }
    public static Result transfer(UUID from, UUID to,String currency,String amount,String reference) {
        try {
            Class<?> api=Class.forName("dev.jdguo.bank.api.MarketSettlement");
            Object r=api.getMethod("transfer",UUID.class,UUID.class,String.class,BigInteger.class,String.class).invoke(null,from,to,currency,new BigInteger(amount),reference);
            return new Result((boolean)r.getClass().getMethod("success").invoke(r),(String)r.getClass().getMethod("code").invoke(r));
        } catch (ReflectiveOperationException|RuntimeException e) {return new Result(false,"bridge_unknown");}
    }
}
