package dev.jdguo.toll;

import com.mojang.brigadier.arguments.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.*;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.*;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.*;
import java.nio.file.Path;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

public final class TollMod implements ModInitializer {
    private static TollMod instance;
    public static void resetTravel(ServerPlayerEntity p){if(instance!=null)instance.samples.remove(p.getUuid());}
    private TollStore store;
    private Path path;
    private boolean healthy;
    private long ticks;
    private record Sample(String dimension,Geometry.Position pos,Entity vehicle) {}
    private final Map<UUID,Sample> samples=new HashMap<>();
    @Override public void onInitialize() {
        instance=this;
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            path=server.getSavePath(WorldSavePath.ROOT).resolve("data/tollmod/state.json");
            try {BankBridge.verifyApi();store=TollStore.load(path);healthy=true;} catch(Exception e){healthy=false;System.err.println("[tollmod] Disabled: "+e);}
            samples.clear();ticks=0;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{samples.clear();healthy=false;store=null;});
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("toll")
            .requires(s->s.hasPermissionLevel(2))
            .then(literal("create").then(argument("name",StringArgumentType.string()).then(argument("radius",DoubleArgumentType.doubleArg(1,128))
                .then(argument("currency",StringArgumentType.string()).suggests((c,b)->{try {for(var v:BankBridge.currencies()){b.suggest("\""+v.name()+"\"");b.suggest("\"id:"+v.id()+"\"");}}catch(Exception ignored){}return b.buildFuture();})
                .then(argument("amount",IntegerArgumentType.integer(1,1000000000)).then(argument("recipient",StringArgumentType.word()).executes(c->create(c.getSource(),StringArgumentType.getString(c,"name"),DoubleArgumentType.getDouble(c,"radius"),StringArgumentType.getString(c,"currency"),IntegerArgumentType.getInteger(c,"amount"),StringArgumentType.getString(c,"recipient")))))))))
            .then(literal("list").executes(c->{if(!ready(c.getSource()))return 0;for(var p:store.points.values())c.getSource().sendFeedback(()->Text.literal(p.name+" | "+p.radius+"格 | "+p.amount+" "+p.currencyName),false);return store.points.size();}))
            .then(literal("info").then(argument("name",StringArgumentType.string()).executes(c->{if(!ready(c.getSource()))return 0;var p=store.points.get(StringArgumentType.getString(c,"name"));if(p==null)return error(c.getSource(),"收费点不存在");c.getSource().sendFeedback(()->Text.literal(p.name+" "+p.dimension+" "+p.x+","+p.y+","+p.z+" 半径="+p.radius+" 上下="+p.height+" 费用="+p.amount+" "+p.currencyName+" ["+p.currency+"] 收款="+p.recipient),false);return 1;})))
            .then(literal("remove").then(argument("name",StringArgumentType.string()).executes(c->{if(!ready(c.getSource()))return 0;var p=store.points.remove(StringArgumentType.getString(c,"name"));if(p==null)return error(c.getSource(),"收费点不存在");if(!save())return error(c.getSource(),"保存失败，收费已暂停");c.getSource().sendFeedback(()->Text.literal("已删除收费点；历史订单保留"),true);return 1;})))
        ));
    }
    private boolean ready(ServerCommandSource s){if(!healthy){error(s,"收费服务不可用，请检查日志/保存文件");return false;}return true;}
    private int error(ServerCommandSource s,String message){s.sendError(Text.literal(message));return 0;}
    private boolean save(){try {store.save(path);return true;}catch(Exception e){healthy=false;System.err.println("[tollmod] Persistence failed; paused: "+e);return false;}}
    private int create(ServerCommandSource s,String name,double radius,String currency,int amount,String recipient){
        if(!ready(s))return 0;
        if(store.points.size()>=128)return error(s,"初版最多支持128个收费点");
        if(store.points.containsKey(name)||name.isBlank()||name.length()>64)return error(s,"名称重复或过长");
        try {
            var player=s.getPlayerOrThrow();
            UUID payee;
            try{payee=UUID.fromString(recipient);}catch(IllegalArgumentException e){var target=s.getServer().getPlayerManager().getPlayer(recipient);if(target==null)return error(s,"名称须为在线玩家，离线收款人请用 UUID");payee=target.getUuid();}
            var cur=BankBridge.resolve(currency,BankBridge.currencies());
            var p=new TollStore.Point();p.name=name;p.radius=radius;p.x=player.getX();p.y=player.getY();p.z=player.getZ();p.dimension=player.getWorld().getRegistryKey().getValue().toString();p.currency=cur.id();p.currencyName=cur.name();p.amount=Integer.toString(amount);p.recipient=payee.toString();
            for(var other:store.points.values()) if(p.dimension.equals(other.dimension)&&Math.abs(p.y-other.y)<=p.height+other.height&&Math.hypot(p.x-other.x,p.z-other.z)<=p.radius+other.radius+4)return error(s,"与已有收费点/缓冲区重叠，请移动中心");
            store.points.put(name,p);if(!save())return error(s,"保存失败，收费暂停");
            // Creation must not immediately bill people already standing in the zone.
            for(var q:s.getServer().getPlayerManager().getPlayerList())if(q.getWorld().getRegistryKey().getValue().toString().equals(p.dimension)&&Geometry.inside(pos(q),p.center(),p.radius+2,p.height+2))store.blocked.add(key(q,p));
            if(!save())return 0;
            s.sendFeedback(()->Text.literal("已启用 "+name+"，半径 "+radius+"，上下各4格；每人一次 "+amount+" "+cur.name()+"。明确欠费没收普通船；箱船暂不没收，保留欠费记录。"),true);return 1;
        }catch(Exception e){return error(s,"创建失败："+e.getMessage());}
    }
    private static Geometry.Position pos(Entity e){return new Geometry.Position(e.getX(),e.getY(),e.getZ());}
    private static String key(ServerPlayerEntity p,TollStore.Point point){return p.getUuid()+":"+point.id;}
    private void tick(MinecraftServer server){
        if(!healthy)return;ticks++;
        Set<UUID> online=new HashSet<>();
        for(var p:server.getPlayerManager().getPlayerList()){
            online.add(p.getUuid());var current=new Sample(p.getWorld().getRegistryKey().getValue().toString(),pos(p),p.getVehicle());var prev=samples.put(p.getUuid(),current);
            if(p.isSpectator())continue;
            for(var point:store.points.values()){
                if(!point.dimension.equals(current.dimension))continue;
                String k=key(p,point);boolean insideBuffer=Geometry.inside(current.pos,point.center(),point.radius+2,point.height+2);
                if(prev==null||!prev.dimension.equals(current.dimension)||distance(prev.pos,current.pos)>64){
                    if(insideBuffer&&store.blocked.add(k)&&!save())return;
                    continue; // login/respawn/dimension or large teleport: no fabricated travel segment
                }
                if(store.blocked.contains(k)){
                    if(!insideBuffer&&ticks%20==0){store.blocked.remove(k);if(!save())return;}continue;
                }
                if(Geometry.crosses(prev.pos,current.pos,point.center(),point.radius,point.height)){
                    if(store.orders.values().stream().anyMatch(o->o.player.equals(p.getUuidAsString())&&o.point.equals(point.id)&&o.status.equals("PENDING")))continue;
                    if(store.orders.size()>=10000){healthy=false;System.err.println("[tollmod] 10000 order safety limit reached; paused, archive with administrator review.");return;}
                    store.blocked.add(k);
                    var o=new TollStore.Order();o.player=p.getUuidAsString();o.recipient=point.recipient;o.currency=point.currency;o.amount=point.amount;o.point=point.id;o.boat=current.vehicle instanceof BoatEntity?current.vehicle.getUuidAsString():null;
                    store.orders.put(o.reference,o);if(!save())return;
                    p.sendMessage(Text.literal("收费点「"+point.name+"」："+point.amount+" "+point.currencyName),false);
                    pay(o,server);if(!healthy)return;
                }
            }
        }
        samples.keySet().retainAll(online);
        if(ticks%100==0)for(var o:new ArrayList<>(store.orders.values()))if(o.status.equals("PENDING")){pay(o,server);if(!healthy)return;}
        // Process after all passengers were billed, never delete a two-seat boat midway through passenger iteration.
        for(var o:store.orders.values())if(o.status.equals("INSUFFICIENT")&&o.boat!=null)confiscate(o,server);
    }
    private static double distance(Geometry.Position a,Geometry.Position b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
    private void pay(TollStore.Order o,MinecraftServer server){
        var r=BankBridge.transfer(UUID.fromString(o.player),UUID.fromString(o.recipient),o.currency,o.amount,o.reference);
        o.lastCode=r.code();
        if(r.success())o.status="PAID";
        else if(r.code().equals("insufficient_funds"))o.status="INSUFFICIENT";
        else if(Set.of("reference_conflict","unknown_currency","invalid_amount","bad_amount","same_account","bad_account").contains(r.code()))o.status="REVIEW";
        // For bank_pending, call the SAME transfer again; it deduplicates and retries persistence.
        // Never promote an in-memory marketReceipt to a durable success.
        if(!save())return;
        var p=server.getPlayerManager().getPlayer(UUID.fromString(o.player));
        if(p!=null&&!o.status.equals("PENDING")){
            p.sendMessage(Text.literal("通行收费："+o.status+" ("+r.code()+")"),false);
            if(o.status.equals("INSUFFICIENT")&&p.getVehicle() instanceof ChestBoatEntity)p.sendMessage(Text.literal("箱船欠费已记录；为保护箱内物品，本版不会没收箱船，请联系管理员处理。"),false);
        }
    }
    private void confiscate(TollStore.Order o,MinecraftServer server){
        if(!healthy)return;
        for(ServerWorld world:server.getWorlds()){
            Entity e=world.getEntity(UUID.fromString(o.boat));if(!(e instanceof BoatEntity boat))continue;
            // Until an audited recovery command exists, never remove a container boat.
            if(boat instanceof ChestBoatEntity)continue;
            // Do not confiscate a boat now occupied by unrelated players after the debtor left.
            if(boat.getPassengerList().stream().noneMatch(p->p.getUuidAsString().equals(o.player)))continue;
            boat.setVelocity(Vec3d.ZERO);
            List<Entity> passengers=new ArrayList<>(boat.getPassengerList());
            List<Vec3d> exits=new ArrayList<>();
            for(int dx=-3;dx<=3&&exits.size()<passengers.size();dx++)for(int dz=-3;dz<=3&&exits.size()<passengers.size();dz++){
                BlockPos feet=boat.getBlockPos().add(dx,0,dz);
                if(world.isChunkLoaded(feet)&&world.getBlockState(feet.down()).isSolidBlock(world,feet.down())&&!world.getBlockState(feet.down()).isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)&&!world.getBlockState(feet.down()).isOf(net.minecraft.block.Blocks.CACTUS)&&world.isAir(feet)&&world.isAir(feet.up()))exits.add(Vec3d.ofBottomCenter(feet));
            }
            if(exits.size()<passengers.size())return; // No verified safe exit: keep boat, retry; never dump passengers into void/water.
            { // Refresh the archive immediately before removal, including inventory changes after a restart.
                var seizure=new TollStore.Seizure();seizure.boat=o.boat;seizure.dimension=world.getRegistryKey().getValue().toString();seizure.reason=o.reference;seizure.entityType=net.minecraft.registry.Registries.ENTITY_TYPE.getId(boat.getType()).toString();
                seizure.nbt=boat.writeNbt(new NbtCompound()).asString();store.seizures.put(o.boat,seizure);if(!save())return;
            }
            for(int i=0;i<passengers.size();i++){var p=passengers.get(i);p.stopRiding();var v=exits.get(i);p.requestTeleport(v.x,v.y,v.z);}
            boat.discard(); // no loot drops: chest inventory preserved in the durable confiscation record
            store.seizures.get(o.boat).status="REMOVED";
            for(var order:store.orders.values())if(o.boat.equals(order.boat)&&order.status.equals("INSUFFICIENT"))order.status="CONFISCATED";
            save();return;
        }
    }
}
