package dev.jdguo.toll;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** Vanilla text displays: no client mod, interaction entity, item drops, or chunk tickets.
 * Call reconcile on the server thread after saved point changes and periodically (e.g. every 20 ticks).
 * Persisted displays in unloaded chunks are reconciled when those chunks load, not force-loaded.
 */
public final class TollHolograms {
    private static final String TAG = "tollmod_hologram";
    private static final String POINT_TAG = "tollmod_point:";
    private static final double LIFT = 2.4;
    private TollHolograms() {}

    /** Stable UUID prevents a delayed entity-load racing reconciliation from spawning a second sign. */
    static UUID entityId(String pointId) {
        return UUID.nameUUIDFromBytes(("tollmod:hologram:" + pointId).getBytes(StandardCharsets.UTF_8));
    }

    public static void reconcile(MinecraftServer server, Collection<TollStore.Point> points) {
        if (server == null || !server.isOnThread()) return;
        Map<String, TollStore.Point> desired = new HashMap<>();
        for (TollStore.Point point : points) {
            if (point != null && point.id != null && point.dimension != null) desired.put(point.id, point);
        }
        for (ServerWorld world : server.getWorlds()) {
            String dimension = world.getRegistryKey().getValue().toString();
            Map<String, DisplayEntity.TextDisplayEntity> found = new HashMap<>();
            // Gather before discarding, because iteration is backed by the world's entity manager.
            ArrayList<DisplayEntity.TextDisplayEntity> loaded = new ArrayList<>();
            world.iterateEntities().forEach(entity -> {
                if (entity instanceof DisplayEntity.TextDisplayEntity display && entity.getCommandTags().contains(TAG)) loaded.add(display);
            });
            for (DisplayEntity.TextDisplayEntity display : loaded) {
                String id = pointId(display);
                TollStore.Point point = desired.get(id);
                if (point == null || !dimension.equals(point.dimension)
                        || !display.getUuid().equals(entityId(id)) || !display.isAlive()
                        || display.squaredDistanceTo(point.x, point.y + LIFT, point.z) > 0.0001
                        || found.putIfAbsent(id, display) != null) {
                    display.discard();
                }
            }
            for (TollStore.Point point : desired.values()) {
                if (!dimension.equals(point.dimension)
                        || !world.isChunkLoaded(MathHelper.floor(point.x) >> 4, MathHelper.floor(point.z) >> 4)) continue;
                DisplayEntity.TextDisplayEntity display = found.get(point.id);
                if (display == null) {
                    // Never remove an unrelated entity just because its UUID conflicts.
                    Entity collision = world.getEntity(entityId(point.id));
                    if (collision != null) continue;
                    display = new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, world);
                    display.setUuid(entityId(point.id));
                    display.addCommandTag(TAG);
                    display.addCommandTag(POINT_TAG + point.id);
                    display.refreshPositionAndAngles(point.x, point.y + LIFT, point.z, 0, 0);
                    style(display, point);
                    world.spawnEntity(display); // false is safe; the next reconcile retries without creating duplicates.
                } else style(display, point);
            }
        }
    }

    /** Removes only loaded toll displays; never loads chunks or touches passengers/player entities. */
    public static void close(MinecraftServer server) {
        if (server == null || !server.isOnThread()) return;
        for (ServerWorld world : server.getWorlds()) {
            ArrayList<Entity> remove = new ArrayList<>();
            world.iterateEntities().forEach(entity -> {
                if (entity instanceof DisplayEntity.TextDisplayEntity && entity.getCommandTags().contains(TAG)) remove.add(entity);
            });
            remove.forEach(Entity::discard);
        }
    }

    private static String pointId(Entity entity) {
        String result = null;
        for (String tag : entity.getCommandTags()) if (tag.startsWith(POINT_TAG)) {
            if (result != null) return ""; // malformed/multiple identity tags are not trusted.
            result = tag.substring(POINT_TAG.length());
        }
        return result == null ? "" : result;
    }

    private static void style(DisplayEntity.TextDisplayEntity display, TollStore.Point point) {
        String currency = point.currencyName == null ? point.currency : point.currencyName;
        String label = "通行收费\n每人 " + point.amount + " " + currency + "\n半径 " + point.radius + " 格 · 向下 " + point.below() + " / 向上 " + point.above() + " 格";
        NbtCompound nbt = new NbtCompound();
        display.writeNbt(nbt);
        nbt.putString("text", Text.Serializer.toJson(Text.literal(label)));
        nbt.putString("billboard", "center");
        nbt.putString("alignment", "center");
        nbt.putInt("line_width", 240);
        nbt.putInt("background", 0x99001820);
        nbt.putByte("text_opacity", (byte) -1);
        nbt.putBoolean("shadow", true);
        nbt.putBoolean("see_through", false);
        nbt.putBoolean("default_background", false);
        nbt.putFloat("view_range", 2.0f);
        nbt.putFloat("shadow_radius", 0.0f);
        display.readNbt(nbt);
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setSilent(true);
    }
}
