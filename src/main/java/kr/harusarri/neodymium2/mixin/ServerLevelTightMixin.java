package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import kr.harusarri.neodymium2.core.EntityLookupTagAccess;
import kr.harusarri.neodymium2.core.ServerLevelTagAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerLevel.class, priority = 900)
public abstract class ServerLevelTightMixin implements ServerLevelTagAccess {
    @Unique private int neo$lastEntitiesLoadedEpoch;
    @Unique private long neo$lastEntitiesLoadedChunkPos = Long.MIN_VALUE;
    @Unique private boolean neo$lastEntitiesLoadedResult;
    @Unique private EntityLookupTagAccess neo$entityLookupTagAccess;

    @Override
    public EntityLookupTagAccess neo$getEntityLookupTagAccess() {
        return this.neo$entityLookupTagAccess;
    }

    @Override
    public void neo$setEntityLookupTagAccess(EntityLookupTagAccess access) {
        this.neo$entityLookupTagAccess = access;
    }

    @Inject(method = "areEntitiesLoaded", at = @At("HEAD"), cancellable = true)
    private void neo$cacheAreEntitiesLoadedDuringBeTick(long chunkPos, CallbackInfoReturnable<Boolean> cir) {
        int epoch = OutsideWorldOptimizer.currentBlockEntityTickEpoch();
        if (epoch != 0 && epoch == this.neo$lastEntitiesLoadedEpoch && chunkPos == this.neo$lastEntitiesLoadedChunkPos) {
            cir.setReturnValue(this.neo$lastEntitiesLoadedResult);
        }
    }

    @Inject(method = "areEntitiesLoaded", at = @At("RETURN"))
    private void neo$storeAreEntitiesLoadedDuringBeTick(long chunkPos, CallbackInfoReturnable<Boolean> cir) {
        int epoch = OutsideWorldOptimizer.currentBlockEntityTickEpoch();
        if (epoch != 0) {
            this.neo$lastEntitiesLoadedEpoch = epoch;
            this.neo$lastEntitiesLoadedChunkPos = chunkPos;
            this.neo$lastEntitiesLoadedResult = cir.getReturnValueZ();
        }
    }

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void neo$shortCircuitStridedOutsideTickChunk(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        if (randomTickSpeed > 0 && OutsideWorldOptimizer.adjustOutsideRandomTickSpeed((ServerLevel) (Object) this, chunk, randomTickSpeed) == 0) {
            ci.cancel();
        }
    }

    @Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true)
    private void neo$throttleOutsidePrecipitation(BlockPos pos, CallbackInfo ci) {
        if (OutsideWorldOptimizer.shouldSkipOutsidePrecipitation((ServerLevel) (Object) this, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "gameEvent", at = @At("HEAD"), cancellable = true)
    private void neo$filterOutsideMobGameEvents(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
        if (OutsideWorldOptimizer.shouldSkipOutsideGameEvent((ServerLevel) (Object) this, event, pos, context)) {
            ci.cancel();
        }
    }
}
