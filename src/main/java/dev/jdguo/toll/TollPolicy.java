package dev.jdguo.toll;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Pure server policy; ownership lookup must use the point's actual server dimension. */
public final class TollPolicy {
    private TollPolicy() {}

    public record Chunk(int x, int z) {}
    public static boolean mayConfiscateBoat(boolean chest,java.util.Collection<String> passengerStates){
        return !chest&&passengerStates!=null&&!passengerStates.isEmpty()
                &&passengerStates.stream().allMatch("INSUFFICIENT"::equals);
    }
    public static boolean shouldEjectChestPassenger(String status,boolean creator,boolean stillAboard){
        return "INSUFFICIENT".equals(status)&&!creator&&stillAboard;
    }
    public static boolean isCreator(String creator,String player){return creator!=null&&creator.equals(player);}
    /** Recipient self-transfers remain no-ops; operator status does not confer any exemption. */
    public static boolean exemptFromNewOrder(TollStore.Point point,String player){
        return isCreator(point.creator,player)||(point.recipient!=null&&point.recipient.equals(player));
    }


    /** Every 16x16 chunk touched by the closed horizontal disk, including boundary touches. */
    public static Set<Chunk> coveredChunks(double x, double z, double radius) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(radius)
                || radius < 1 || radius > 128 || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000)
            throw new IllegalArgumentException("Invalid toll position/radius");
        // ceil(min/16)-1 deliberately includes the chunk immediately left of an exact boundary.
        int minX = (int) Math.ceil((x - radius) / 16.0) - 1;
        int maxX = (int) Math.floor((x + radius) / 16.0);
        int minZ = (int) Math.ceil((z - radius) / 16.0) - 1;
        int maxZ = (int) Math.floor((z + radius) / 16.0);
        Set<Chunk> result = new LinkedHashSet<>();
        double squared = radius * radius;
        for (int cx = minX; cx <= maxX; cx++) {
            double left = cx * 16.0, right = left + 16.0;
            double dx = x < left ? left - x : x > right ? x - right : 0;
            for (int cz = minZ; cz <= maxZ; cz++) {
                double top = cz * 16.0, bottom = top + 16.0;
                double dz = z < top ? top - z : z > bottom ? z - bottom : 0;
                if (dx * dx + dz * dz <= squared) result.add(new Chunk(cx, cz));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** Strict owner equality, not build trust or operator bypass. Lookup failures deny charging. */
    public static boolean whollyOwned(UUID owner, double x, double z, double radius,
                                      Function<Chunk, UUID> ownerAt) {
        if (owner == null || ownerAt == null) return false;
        try {
            for (Chunk chunk : coveredChunks(x, z, radius))
                if (!owner.equals(ownerAt.apply(chunk))) return false;
            return true;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Positive plain decimal. At most four entered decimals; physical units must be integral. */
    public static BigDecimal parseAmount(String text, boolean physical) {
        // Bound textual input before constructing arbitrary-precision values.
        if (text == null || text.length() > 64 || !text.matches("[0-9]+(?:\\.[0-9]{1,4})?"))
            throw new IllegalArgumentException("Amount must be a positive decimal with at most four places");
        BigDecimal amount = new BigDecimal(text).stripTrailingZeros();
        if (amount.signum() <= 0 || (physical && amount.scale() > 0))
            throw new IllegalArgumentException("Physical currency must use positive whole units");
        return amount;
    }
}
