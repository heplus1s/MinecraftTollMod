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
import java.math.BigDecimal;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

public final class TollMod implements ModInitializer {
    private static TollMod instance;
    public static void resetTravel(ServerPlayerEntity p){if(instance!=null)instance.samples.remove(p.getUuid());}
    private TollStore store;
    private Path path;
    private boolean healthy;
    private boolean initializeOnNextTick;
    private TollStateRepository repository;
    private long ticks;
    private record Sample(String dimension,Geometry.Position pos,Entity vehicle) {}
    private final Map<UUID,Sample> samples=new HashMap<>();
    private record Passage(Set<String> passengers,Set<String> exempt,Map<String,TollStore.Order> orders) {}
    private final Map<String,Passage> orderPassages=new HashMap<>();
    private final Map<String,TollStore.Order> liveEjections=new HashMap<>();
    private final Map<String,Long> seizureWindows=new HashMap<>(); // live passage only; never resurrect punishment after restart
    @Override public void onInitialize() {
        instance=this;
        ServerLifecycleEvents.SERVER_STARTED.register(server->{
            path=server.getSavePath(WorldSavePath.ROOT).resolve("data/tollmod/state.json");
            // All SERVER_STARTED callbacks must finish before inspecting the bank runtime.
            // Dependency presence does not establish an event-listener execution order.
            healthy=false;store=null;repository=null;initializeOnNextTick=true;
            samples.clear();seizureWindows.clear();orderPassages.clear();liveEjections.clear();ticks=0;
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(TollHolograms::close);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{samples.clear();orderPassages.clear();liveEjections.clear();healthy=false;store=null;initializeOnNextTick=false;});
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->dispatcher.register(literal("toll")
            .then(automaticCommand("private"))
            .then(automaticCommand("system").requires(source->source.hasPermissionLevel(2)))
            .then(literal("create").requires(s->s.hasPermissionLevel(2)).then(argument("name",StringArgumentType.string()).then(argument("radius",DoubleArgumentType.doubleArg(1,128))
                .then(argument("currency",StringArgumentType.string()).suggests((c,b)->{try {for(var v:BankBridge.currencies()){b.suggest("\""+v.name()+"\"");b.suggest("\"id:"+v.id()+"\"");}}catch(Exception ignored){}return b.buildFuture();})
                .then(argument("amount",IntegerArgumentType.integer(1,1000000000)).then(argument("recipient",StringArgumentType.word()).executes(c->create(c.getSource(),StringArgumentType.getString(c,"name"),DoubleArgumentType.getDouble(c,"radius"),StringArgumentType.getString(c,"currency"),IntegerArgumentType.getInteger(c,"amount"),StringArgumentType.getString(c,"recipient")))))))))
            .then(literal("list").executes(c->{if(!ready(c.getSource()))return 0;for(var p:store.points.values())if(canManage(c.getSource(),p))c.getSource().sendFeedback(()->Text.literal(p.name+" | "+p.footprint()+" | "+p.amount+" "+p.currencyName),false);return store.points.size();}))
            .then(literal("info").then(argument("name",StringArgumentType.string()).executes(c->{if(!ready(c.getSource()))return 0;var p=store.points.get(StringArgumentType.getString(c,"name"));if(p==null||!canManage(c.getSource(),p))return error(c.getSource(),"收费点不存在或无权管理");c.getSource().sendFeedback(()->Text.literal(p.name+" "+p.dimension+" "+p.x+","+p.y+","+p.z+" "+p.footprint()+" 向下="+p.below()+" 向上="+p.above()+" 费用="+p.amount+" "+p.currencyName+" ["+p.currency+"] 收款="+p.recipient+" 创建者="+(p.creator==null?"历史数据未记录":p.creator)),false);return 1;})))
            .then(literal("remove")
                .then(literal("nearby").executes(c->removeNearby(c.getSource())))
                .then(argument("name",StringArgumentType.string()).executes(c->removeNamed(c.getSource(),StringArgumentType.getString(c,"name")))))
        ));
    }
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> automaticCommand(String mode) {
        return literal(mode).then(argument("currency",StringArgumentType.string())
            .suggests((c,b)->{try {for(var v:BankBridge.currencies())b.suggest("\"id:"+v.id()+"\"");}catch(Exception ignored){}return b.buildFuture();})
            .then(argument("amount",StringArgumentType.word())
                .executes(c->createAutomatic(c.getSource(),mode,3,null,2,2,StringArgumentType.getString(c,"currency"),StringArgumentType.getString(c,"amount")))
                .then(argument("radius",DoubleArgumentType.doubleArg(1,128))
                    .executes(c->createAutomatic(c.getSource(),mode,DoubleArgumentType.getDouble(c,"radius"),null,2,2,StringArgumentType.getString(c,"currency"),StringArgumentType.getString(c,"amount")))
                    .then(argument("below",DoubleArgumentType.doubleArg(0,128)).then(argument("above",DoubleArgumentType.doubleArg(0,128))
                        .executes(c->createAutomatic(c.getSource(),mode,DoubleArgumentType.getDouble(c,"radius"),null,DoubleArgumentType.getDouble(c,"below"),DoubleArgumentType.getDouble(c,"above"),StringArgumentType.getString(c,"currency"),StringArgumentType.getString(c,"amount"))))))
                .then(boxCommand(mode))));
    }
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> boxCommand(String mode) {
        return literal("box").then(argument("halfX",DoubleArgumentType.doubleArg(1,128))
            .then(argument("halfZ",DoubleArgumentType.doubleArg(1,128))
                .executes(c->createBox(c,mode,2,2))
                .then(argument("below",DoubleArgumentType.doubleArg(0,128))
                    .then(argument("above",DoubleArgumentType.doubleArg(0,128))
                        .executes(c->createBox(c,mode,DoubleArgumentType.getDouble(c,"below"),DoubleArgumentType.getDouble(c,"above")))))));
    }
    private int createBox(com.mojang.brigadier.context.CommandContext<ServerCommandSource> c,String mode,double below,double above){
        return createAutomatic(c.getSource(),mode,DoubleArgumentType.getDouble(c,"halfX"),DoubleArgumentType.getDouble(c,"halfZ"),
                below,above,StringArgumentType.getString(c,"currency"),StringArgumentType.getString(c,"amount"));
    }
    private int removeNearby(ServerCommandSource source) {
        if (!ready(source)) return 0;
        if (!(source.getEntity() instanceof ServerPlayerEntity player))
            return error(source, "就近删除须由玩家在游戏内执行");
        var dimension = player.getWorld().getRegistryKey().getValue().toString();
        var selection = TollNearby.select(store.points.values(), dimension, pos(player));
        if (selection.ambiguous()) return error(source, "有多个收费点同样近，请按收费点编号删除");
        if (selection.point() == null) return error(source, "脚下没有收费区，10 格内也没有收费点中心");
        return removePoint(source, selection.point());
    }

    private int removeNamed(ServerCommandSource source, String name) {
        if (!ready(source)) return 0;
        return removePoint(source, store.points.get(name));
    }

    private int removePoint(ServerCommandSource source, TollStore.Point point) {
        // Authorize the selected target rather than falling through to a different one.
        if (point == null || !canManage(source, point))
            return error(source, "收费点不存在或无权管理");
        store.points.remove(point.name);
        store.blocked.removeIf(key -> key.endsWith(":" + point.id));
        if (!save()) return error(source, "保存失败，收费已暂停");
        source.sendFeedback(() -> Text.literal("已删除收费点：" + point.name + "；历史订单保留"), true);
        return 1;
    }

    private boolean canManage(ServerCommandSource s,TollStore.Point p){
        return s.hasPermissionLevel(2) || ("private".equals(p.mode) && s.getEntity() instanceof ServerPlayerEntity player && player.getUuidAsString().equals(p.owner));
    }
    private int createAutomatic(ServerCommandSource s,String mode,double radius,Double halfZ,double below,double above,String currency,String input) {
        if(!ready(s))return 0;
        if("system".equals(mode)&&!BankBridge.modernApi())return error(s,"银行尚未提供收费系统账户接口，不能创建系统收费点，请升级配套银行模组");
        try {
            if(!(s.getEntity() instanceof ServerPlayerEntity))return error(s,"创建收费点须由玩家执行；位置取玩家当前位置");
            var player=s.getPlayerOrThrow();var cur=BankBridge.resolve(currency,BankBridge.currencies());
            BigDecimal amount=TollPolicy.parseAmount(input,!cur.id().equals("note")&&!cur.id().startsWith("personal:"));
            if(amount.compareTo(new BigDecimal("1000000000"))>0)return error(s,"金额不能超过1000000000");
            if(!BankBridge.modernApi()&&amount.stripTrailingZeros().scale()>0)return error(s,"当前银行接口仅支持整数收费，请升级配套银行模组");
            if(below+above<=0)return error(s,"上下范围不能同时为0");
            if("system".equals(mode)){var preview=BankBridge.previewSystem(cur.id(),amount);if(!preview.success())return error(s,"当前无法以该金额结算系统收费："+preview.code()+"。未创建收费点，未扣款。");}
            var p=new TollStore.Point();p.below=below;p.above=above;p.name=p.id;p.mode=mode;p.owner=player.getUuidAsString();p.creator=player.getUuidAsString();p.recipient="private".equals(mode)?p.owner:null;
            p.radius=radius;p.halfZ=halfZ;p.x=player.getX();p.y=player.getY();p.z=player.getZ();p.dimension=player.getWorld().getRegistryKey().getValue().toString();
            p.currency=cur.id();p.currencyName=cur.name();p.amount=amount.toPlainString();
            if("private".equals(mode)&&!LandBridge.ownsArea(s.getServer(),player.getUuid(),p))return error(s,"收费范围必须完全位于你自己的领地内；领地接口不可用时禁止创建");
            if(store.points.size()>=128)return error(s,"最多支持128个收费点");
            for(var other:store.points.values())if(Geometry.overlaps(p,other,2))return error(s,"与其他收费点或缓冲区重叠");
            store.points.put(p.name,p);
            for(var q:s.getServer().getPlayerManager().getPlayerList())if(q.getWorld().getRegistryKey().getValue().toString().equals(p.dimension)&&p.inside(pos(q),2))store.blocked.add(key(q,p));
            if(!save())return error(s,"保存失败，收费暂停");
            s.sendFeedback(()->Text.literal("已创建"+("private".equals(mode)?"私人":"系统")+"收费点 "+p.name+"；每人 "+p.amount+" "+p.currencyName+"，"+p.footprint()+"，向下"+p.below()+"、向上"+p.above()+"格"),true);return 1;
        }catch(Exception invalid){return error(s,"创建失败："+invalid.getMessage());}
    }
    private boolean ready(ServerCommandSource s){if(!healthy){error(s,"收费服务不可用，请检查日志/保存文件");return false;}return true;}
    private int error(ServerCommandSource s,String message){s.sendError(Text.literal(message));return 0;}
    private boolean save(){try {
        if(store.blocked.size()>100000)throw new IllegalStateException("去重记录达到安全上限");
        repository.save();
        return true;}catch(Exception e){healthy=false;System.err.println("[tollmod] Persistence failed; paused: "+e);return false;}}
    private int create(ServerCommandSource s,String name,double radius,String currency,int amount,String recipient){
        if(!ready(s))return 0;
        if(store.points.size()>=128)return error(s,"初版最多支持128个收费点");
        if(store.points.containsKey(name)||name.isBlank()||name.length()>64)return error(s,"名称重复或过长");
        try {
            var player=s.getPlayerOrThrow();
            UUID payee;
            try{payee=UUID.fromString(recipient);}catch(IllegalArgumentException e){var target=s.getServer().getPlayerManager().getPlayer(recipient);if(target==null)return error(s,"名称须为在线玩家，离线收款人请用 UUID");payee=target.getUuid();}
            var cur=BankBridge.resolve(currency,BankBridge.currencies());
            var p=new TollStore.Point();p.creator=player.getUuidAsString();p.name=name;p.radius=radius;p.x=player.getX();p.y=player.getY();p.z=player.getZ();p.dimension=player.getWorld().getRegistryKey().getValue().toString();p.currency=cur.id();p.currencyName=cur.name();p.amount=Integer.toString(amount);p.recipient=payee.toString();
            for(var other:store.points.values()) if(Geometry.overlaps(p,other,2))return error(s,"与已有收费点/缓冲区重叠，请移动中心");
            store.points.put(name,p);if(!save())return error(s,"保存失败，收费暂停");
            // Creation must not immediately bill people already standing in the zone.
            for(var q:s.getServer().getPlayerManager().getPlayerList())if(q.getWorld().getRegistryKey().getValue().toString().equals(p.dimension)&&p.inside(pos(q),2))store.blocked.add(key(q,p));
            if(!save())return 0;
            s.sendFeedback(()->Text.literal("已启用 "+name+"，半径 "+radius+"，上下各4格；每人一次 "+amount+" "+cur.name()+"。普通船全员明确欠费才没收，否则仅欠费者下船；箱船仅让欠费乘客直接下船，不移除船或物品。"),true);return 1;
        }catch(Exception e){return error(s,"创建失败："+e.getMessage());}
    }
    private static Geometry.Position pos(Entity e){return new Geometry.Position(e.getX(),e.getY(),e.getZ());}
    private static String key(ServerPlayerEntity p,TollStore.Point point){return p.getUuid()+":"+point.id;}
    private void tick(MinecraftServer server){
        if(initializeOnNextTick){
            // Exactly one attempt: do not retry uncertain migration/persistence failures.
            initializeOnNextTick=false;
            try {
                BankBridge.verifyApi();repository=TollStateRepository.open(path);store=repository.state;healthy=true;
            } catch(Exception e){healthy=false;System.err.println("[tollmod] Disabled: "+e);}
        }
        if(!healthy)return;ticks++;
        if(ticks%20==0)TollHolograms.reconcile(server,store.points.values());
        Map<String,Passage> newPassages=new HashMap<>();
        Set<UUID> online=new HashSet<>();
        for(var p:server.getPlayerManager().getPlayerList()){
            online.add(p.getUuid());var current=new Sample(p.getWorld().getRegistryKey().getValue().toString(),pos(p),p.getVehicle());var prev=samples.put(p.getUuid(),current);
            if(p.isSpectator())continue;
            for(var point:store.points.values()){
                if(!point.dimension.equals(current.dimension))continue;
                if(TollPolicy.exemptFromNewOrder(point,p.getUuidAsString()))continue; // before orders, balance checks, or vehicle enforcement
                String k=key(p,point);boolean insideBuffer=point.inside(current.pos,2);
                if(prev==null||!prev.dimension.equals(current.dimension)||distance(prev.pos,current.pos)>64){
                    if(insideBuffer&&store.blocked.add(k)&&!save())return;
                    continue; // login/respawn/dimension or large teleport: no fabricated travel segment
                }
                if(store.blocked.contains(k)){
                    if(!insideBuffer&&ticks%20==0){store.blocked.remove(k);if(!save())return;}continue;
                }
                if(point.crosses(prev.pos,current.pos)){
                    if("private".equals(point.mode)&&!LandBridge.ownsArea(server,UUID.fromString(point.owner),point)){
                        store.blocked.add(k);if(!save())return;continue; // do not charge a stationary player if ownership later returns
                    }
                    if(store.orders.values().stream().anyMatch(o->o.player.equals(p.getUuidAsString())&&o.point.equals(point.id)&&o.status.equals("PENDING")))continue;
                    if(store.orders.size()>=10000){healthy=false;System.err.println("[tollmod] 10000 order safety limit reached; paused, archive with administrator review.");return;}
                    store.blocked.add(k);
                    var o=new TollStore.Order();o.transport=BankBridge.modernApi()?"toll-v1":"legacy";o.mode=point.mode;o.creator=point.creator;o.systemCreatorChargeAllowed="system".equals(point.mode);o.player=p.getUuidAsString();o.recipient=point.recipient;o.currency=point.currency;o.amount=point.amount;o.point=point.id;o.boat=current.vehicle instanceof BoatEntity?current.vehicle.getUuidAsString():null;
                    if(o.boat!=null){
                        Passage passage=newPassages.computeIfAbsent(o.boat+":"+point.id,unused->{
                            Set<String> passengers=new HashSet<>(),exempt=new HashSet<>();
                            for(Entity rider:current.vehicle.getPassengerList()){
                                passengers.add(rider.getUuidAsString());
                                if(TollPolicy.exemptFromNewOrder(point,rider.getUuidAsString()))exempt.add(rider.getUuidAsString());
                            }
                            return new Passage(passengers,exempt,new HashMap<>());
                        });
                        passage.orders.put(o.player,o);orderPassages.put(o.reference,passage);
                    }
                    store.orders.put(o.reference,o);if(!save())return;
                    if(o.boat!=null)seizureWindows.put(o.reference,ticks+100);
                    pay(o,server);if(!healthy)return;
                }
            }
        }
        samples.keySet().retainAll(online);
        seizureWindows.entrySet().removeIf(e->{
            var order=store.orders.get(e.getKey());if(order==null||e.getValue()<ticks||Set.of("PAID","REVIEW","CONFISCATED").contains(order.status))return true;
            var rider=server.getPlayerManager().getPlayer(UUID.fromString(order.player));
            return rider==null||rider.getVehicle()==null||!rider.getVehicle().getUuidAsString().equals(order.boat);
        });
        if(ticks%100==0)for(var o:new ArrayList<>(store.orders.values()))if(o.status.equals("PENDING")){pay(o,server);if(!healthy)return;}
        orderPassages.keySet().retainAll(seizureWindows.keySet());
        // Process after all passengers were billed, never delete a two-seat boat midway through passenger iteration.
        for(var o:store.orders.values())if(o.status.equals("INSUFFICIENT")&&o.boat!=null)confiscate(o,server);
        // Same live passage: remounting does not create a second charge or bypass ejection.
        liveEjections.entrySet().removeIf(entry->{
            var order=entry.getValue();var rider=server.getPlayerManager().getPlayer(UUID.fromString(order.player));
            var point=store.points.values().stream().filter(v->v.id.equals(order.point)).findFirst().orElse(null);
            if(rider==null||point==null||!rider.getWorld().getRegistryKey().getValue().toString().equals(point.dimension)
                    ||!point.inside(pos(rider),2))return true;
            if(rider.getVehicle()!=null&&rider.getVehicle().getUuidAsString().equals(order.boat))rider.stopRiding();
            return false;
        });
    }
    private static double distance(Geometry.Position a,Geometry.Position b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
    private void pay(TollStore.Order o,MinecraftServer server){
        if(TollPolicy.exemptOrder(o)){
            // Older pending orders may already have a bank receipt. Never retry a new charge,
            // invent a refund, or seize a boat; retain the reference for manual receipt review.
            o.status="REVIEW";o.lastCode="payer_exempt_receipt_review";seizureWindows.remove(o.reference);save();return;
        }
        boolean firstAttempt=o.lastCode==null||o.lastCode.isEmpty();
        var r=BankBridge.charge(o);
        o.lastCode=r.code();
        if(r.success())o.status="PAID";
        else if(r.code().equals("insufficient_funds"))o.status="INSUFFICIENT";
        else if(Set.of("reference_conflict","unknown_currency","invalid_amount","bad_amount","same_account","bad_account","unsupported_system","unsupported_precision","invalid","currency","bad_reference","unsupported_settlement","insufficient_treasury","insufficient_backing","no_current_rate","below_precision","paused").contains(r.code()))o.status="REVIEW";
        // For bank_pending, call the SAME transfer again; it deduplicates and retries persistence.
        // Never promote an in-memory marketReceipt to a durable success.
        if(!save())return;
        var p=server.getPlayerManager().getPlayer(UUID.fromString(o.player));
        if(p!=null&&(!o.status.equals("PENDING")||firstAttempt)){
            var point=store.points.values().stream().filter(v->v.id.equals(o.point)).findFirst().orElse(null);
            TollChat.send(p,server,o,point);
            if(o.status.equals("INSUFFICIENT")&&p.getVehicle() instanceof ChestBoatEntity)p.sendMessage(Text.literal("箱船通行费不足：仅欠费乘客直接下船；船、箱内物品及已付款乘客保留。"),false);
        }
    }
    private void ejectUnpaidPassenger(TollStore.Order order,ServerPlayerEntity debtor,BoatEntity boat){
        if(!TollPolicy.shouldEjectChestPassenger(order.status,TollPolicy.exemptOrder(order),
                debtor.getVehicle()==boat && boat.getPassengerList().contains(debtor)))return;
        // Requested policy: immediate dismount, with no safe-ground search or teleport.
        // Do not modify the boat, its inventory, or other passengers.
        debtor.stopRiding();
        order.status="REVIEW";order.lastCode="passenger_ejected";
        seizureWindows.remove(order.reference);liveEjections.put(order.reference,order);
        save(); // no replay after restart; no new payment reference or second debit
        debtor.sendMessage(Text.literal("通行费不足，已直接下船。船和物品保留，其他乘客不会因本次处理再次扣费。"),false);
    }
    private void confiscate(TollStore.Order o,MinecraftServer server){
        if(!healthy)return;
        if(TollPolicy.exemptOrder(o)){o.status="REVIEW";o.lastCode="payer_exempt";seizureWindows.remove(o.reference);save();return;}
        var debtor=server.getPlayerManager().getPlayer(UUID.fromString(o.player));
        Long expires=seizureWindows.get(o.reference);
        if(expires==null||ticks>expires||debtor==null||debtor.getVehicle()==null||!debtor.getVehicle().getUuidAsString().equals(o.boat)){
            o.status="REVIEW";o.lastCode="seizure_window_closed";seizureWindows.remove(o.reference);save();return;
        }
        for(ServerWorld world:server.getWorlds()){
            Entity e=world.getEntity(UUID.fromString(o.boat));if(!(e instanceof BoatEntity boat))continue;
            // Container boats are never archived, removed, or emptied. Only this debtor exits.
            Passage passage=orderPassages.get(o.reference);
            List<String> states=new ArrayList<>();
            Set<String> aboard=new HashSet<>();for(Entity rider:boat.getPassengerList())aboard.add(rider.getUuidAsString());
            if(passage!=null&&aboard.equals(passage.passengers)){
                for(String rider:passage.passengers){
                    var billed=passage.orders.get(rider);
                    states.add(passage.exempt.contains(rider)?"EXEMPT":billed==null?"UNKNOWN":billed.status);
                }
            }else states.add("UNKNOWN");
            if(!TollPolicy.mayConfiscateBoat(boat instanceof ChestBoatEntity,states)){
                ejectUnpaidPassenger(o,debtor,boat);return;
            }
            // Do not confiscate a boat now occupied by unrelated players after the debtor left.
            if(boat.getPassengerList().stream().noneMatch(p->p.getUuidAsString().equals(o.player)))continue;
            boat.setVelocity(Vec3d.ZERO);
            List<Entity> passengers=new ArrayList<>(boat.getPassengerList());
            List<Vec3d> exits=new ArrayList<>();
            List<Box> exitBoxes=new ArrayList<>();
            for(int dx=-3;dx<=3&&exits.size()<passengers.size();dx++)for(int dz=-3;dz<=3&&exits.size()<passengers.size();dz++){
                BlockPos feet=boat.getBlockPos().add(dx,0,dz);
                if(world.isChunkLoaded(feet)&&world.getBlockState(feet.down()).isSolidBlock(world,feet.down())&&!world.getBlockState(feet.down()).isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)&&!world.getBlockState(feet.down()).isOf(net.minecraft.block.Blocks.CACTUS)&&world.isAir(feet)&&world.isAir(feet.up())){
                    var passenger=passengers.get(exits.size());var target=Vec3d.ofBottomCenter(feet);
                    var box=passenger.getBoundingBox().offset(target.x-passenger.getX(),target.y-passenger.getY(),target.z-passenger.getZ());
                    boolean distinct=exitBoxes.stream().noneMatch(b->b.intersects(box));
                    int minX=MathHelper.floor(box.minX)>>4,maxX=MathHelper.floor(box.maxX)>>4,minZ=MathHelper.floor(box.minZ)>>4,maxZ=MathHelper.floor(box.maxZ)>>4;
                    boolean loaded=maxX-minX<=4&&maxZ-minZ<=4;
                    for(int cx=minX;loaded&&cx<=maxX;cx++)for(int cz=minZ;loaded&&cz<=maxZ;cz++)loaded=world.isChunkLoaded(cx,cz);
                    if(distinct&&loaded&&world.getWorldBorder().contains(box)&&world.isSpaceEmpty(passenger,box)){exits.add(target);exitBoxes.add(box);}
                }
            }
            if(exits.size()<passengers.size())return; // No verified safe exit: keep boat, retry; never dump passengers into void/water.
            { // Refresh the archive immediately before removal, including inventory changes after a restart.
                var seizure=new TollStore.Seizure();seizure.boat=o.boat;seizure.dimension=world.getRegistryKey().getValue().toString();seizure.reason=o.reference;seizure.entityType=net.minecraft.registry.Registries.ENTITY_TYPE.getId(boat.getType()).toString();
                seizure.nbt=boat.writeNbt(new NbtCompound()).asString();store.seizures.put(o.boat,seizure);if(!save())return;
            }
            for(var passenger:passengers)if(passenger instanceof ServerPlayerEntity rider)rider.sendMessage(Text.literal("本船全体乘客通行费余额不足，本船将移除；全部乘客将安全下船。已付通行费不自动退还。"),false);
            for(int i=0;i<passengers.size();i++){var p=passengers.get(i);p.stopRiding();var v=exits.get(i);p.requestTeleport(v.x,v.y,v.z);}
            boat.discard(); // Ordinary boats only; chest boats are never removed.
            store.seizures.get(o.boat).status="REMOVED";
            for(var order:store.orders.values())if(o.boat.equals(order.boat)&&order.status.equals("INSUFFICIENT"))order.status="CONFISCATED";
            seizureWindows.remove(o.reference);save();return;
        }
    }
}
