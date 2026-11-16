package dev.jdguo.toll.mixin;
import dev.jdguo.toll.TollMod;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayNetworkHandler.class)
public abstract class TeleportMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method="requestTeleport(DDDFFLjava/util/Set;)V",at=@At("HEAD"))
    private void toll$reset(CallbackInfo ci){TollMod.resetTravel(player);}
}
