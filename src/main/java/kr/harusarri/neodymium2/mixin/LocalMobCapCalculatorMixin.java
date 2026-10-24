package kr.harusarri.neodymium2.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import kr.harusarri.neodymium2.RegionSpawner;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LocalMobCapCalculator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/**
 * Optimizes LocalMobCapCalculator.getPlayersNear(ChunkPos) with a 1-slot last-chunk cache
 * and non-capturing map lookup, eliminating per-mob lambda allocations and repeated
 * Long2ObjectOpenHashMap lookups when consecutive mobs reside in the same chunk.
 * 100% mathematically identical to Vanilla.
 */
@Mixin(LocalMobCapCalculator.class)
public abstract class LocalMobCapCalculatorMixin {
    @Shadow @Final private Long2ObjectMap<List<ServerPlayer>> playersNearChunk;
    @Shadow @Final private ChunkMap chunkMap;

    @Unique private long neo$lastChunkKey = Long.MIN_VALUE;
    @Unique private List<ServerPlayer> neo$lastPlayers;

    @Inject(method = "getPlayersNear", at = @At("HEAD"), cancellable = true)
    private void neo$fastGetPlayersNear(ChunkPos pos, CallbackInfoReturnable<List<ServerPlayer>> cir) {
        long key = pos.pack();
        if (key == this.neo$lastChunkKey && this.neo$lastPlayers != null) {
            RegionSpawner.onFastLocalMobCapHit();
            cir.setReturnValue(this.neo$lastPlayers);
            return;
        }
        List<ServerPlayer> list = this.playersNearChunk.get(key);
        if (list == null) {
            list = this.chunkMap.getPlayersCloseForSpawning(pos);
            this.playersNearChunk.put(key, list);
        } else {
            RegionSpawner.onFastLocalMobCapHit();
        }
        this.neo$lastChunkKey = key;
        this.neo$lastPlayers = list;
        cir.setReturnValue(list);
    }
}
