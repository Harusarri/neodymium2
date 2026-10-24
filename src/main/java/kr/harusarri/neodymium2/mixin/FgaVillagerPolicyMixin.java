package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.VillagerLobotomy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional FGA compatibility: one owner for Brain, restock, movement and gifts. */
@Pseudo
@Mixin(targets="carpet.fga.VillagerTradeOnlyManager",remap=false)
public abstract class FgaVillagerPolicyMixin {
    @Shadow private static boolean release(Villager v){throw new AssertionError();}
    @Inject(method="tickBrain",at=@At("HEAD"),cancellable=true)
    private static void neo$brain(Brain<LivingEntity> brain,ServerLevel level,Villager v,CallbackInfo ci){
        if(VillagerLobotomy.enabled()){brain.tick(level,v);ci.cancel();}
    }
    @Inject(method="tickRestock",at=@At("HEAD"),cancellable=true)
    private static void neo$singlePolicy(Villager v,CallbackInfo ci){
        if(VillagerLobotomy.enabled()){release(v);ci.cancel();}
    }
    @Inject(method="applyStatic",at=@At("HEAD"),cancellable=true)
    private static void neo$noStaticTeleport(Villager v,CallbackInfo ci){if(VillagerLobotomy.enabled())ci.cancel();}
}
