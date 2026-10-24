package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.VillagerLobotomy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(ZombieVillager.class)
public abstract class CureCreditMixin {
    @Shadow private UUID conversionStarter;
    @Inject(method="lambda$finishConversion$0",at=@At("TAIL"))
    private void neo$offlineCureCredit(ServerLevel level,Villager villager,CallbackInfo ci){
        if(VillagerLobotomy.enabled()&&conversionStarter!=null&&level.getPlayerByUUID(conversionStarter)==null){
            // The normal event already credits a player present in this dimension.
            // Use the same capped gossip types for an offline / other-dimension owner.
            villager.getGossips().add(conversionStarter,GossipType.MAJOR_POSITIVE,20);
            villager.getGossips().add(conversionStarter,GossipType.MINOR_POSITIVE,25);
        }
    }
}
