package dev.jdguo.toll;
import java.lang.reflect.*;
import java.math.BigInteger;
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
    public static List<Currency> currencies() throws ReflectiveOperationException {
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
    public static Result transfer(UUID from, UUID to,String currency,String amount,String reference) {
        try {
            Class<?> api=Class.forName("dev.jdguo.bank.api.MarketSettlement");
            Object r=api.getMethod("transfer",UUID.class,UUID.class,String.class,BigInteger.class,String.class).invoke(null,from,to,currency,new BigInteger(amount),reference);
            return new Result((boolean)r.getClass().getMethod("success").invoke(r),(String)r.getClass().getMethod("code").invoke(r));
        } catch (ReflectiveOperationException|RuntimeException e) {return new Result(false,"bridge_unknown");}
    }
}
