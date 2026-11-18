package dev.jdguo.toll;

import net.minecraft.server.MinecraftServer;
import java.util.UUID;

/** Missing/incompatible land integration fails closed for private tolls. */
public final class LandBridge {
    private LandBridge() {}
    public static boolean ownsArea(MinecraftServer server, UUID owner, TollStore.Point point) {
        try {
            var api=Class.forName("dev.jdguo.land.api.LandApi");
            var lookup=api.getMethod("parcelAt", MinecraftServer.class,String.class,int.class,int.class);
            for (var chunk:TollPolicy.coveredChunks(point.x,point.z,point.radius)) {
                Object parcel=lookup.invoke(null,server,point.dimension,chunk.x(),chunk.z());
                if(parcel==null || !owner.equals(parcel.getClass().getMethod("owner").invoke(parcel)))return false;
            }
            return true;
        } catch (ReflectiveOperationException|RuntimeException unavailable) { return false; }
    }
}
