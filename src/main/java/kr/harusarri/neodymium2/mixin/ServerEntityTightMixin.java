package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideEntityOptimizer;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;

/**
 * Priority 400 ensures neo$strideStaticCleanDecorativeTracker executes BEFORE polymer-virtual-entity
 * (polymerVE$tick) and polymer-core (polymer$tickHead) at default priority 1000, avoiding redundant
 * VirtualEntity HolderAttachment iterations on stationary clean hologram/display/mob entities.
 */
@Mixin(value = ServerEntity.class, priority = 400)
public abstract class ServerEntityTightMixin {
    @Shadow @Final private Entity entity;
    @Shadow private int tickCount;
    @Shadow private List<Entity> lastPassengers;
    @Shadow @Final private VecDeltaCodec positionCodec;
    @Shadow private byte lastSentYRot;
    @Shadow private byte lastSentXRot;
    @Shadow private byte lastSentYHeadRot;
    @Shadow private Vec3 lastSentMovement;
    @Shadow private boolean wasOnGround;
    @Shadow @Final private boolean trackDelta;

    @Inject(method = "sendChanges", at = @At("HEAD"), cancellable = true)
    private void neo$strideStaticCleanDecorativeTracker(CallbackInfo ci) {
        if (OutsideEntityOptimizer.shouldSkipStaticTrackerSendChanges(this.entity, this.tickCount, this.lastPassengers)
            || OutsideEntityOptimizer.shouldSkipStationaryMobTrackerSendChanges(
                this.entity,
                this.tickCount,
                this.lastPassengers,
                this.positionCodec.getBase(),
                this.lastSentYRot,
                this.lastSentXRot,
                this.lastSentYHeadRot,
                this.lastSentMovement,
                this.wasOnGround,
                this.trackDelta
            )) {
            this.tickCount++;
            ci.cancel();
        }
    }
}
