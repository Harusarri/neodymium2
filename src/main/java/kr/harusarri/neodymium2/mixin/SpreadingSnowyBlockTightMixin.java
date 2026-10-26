package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.Neodymium2;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SnowyBlock;
import net.minecraft.world.level.block.SpreadingSnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paper / Purpur Chunk-Aware Grass & Mycelium RandomTick Spread (CHUNK_OUTSIDE only):
 * 1. Inside vanillaSensitive regions, runs 100% untouched Vanilla.
 * 2. In CHUNK_OUTSIDE, caches the resolved baseBlock (Blocks.DIRT) and reuses the current LevelChunk
 *    reference for in-chunk block state lookups while skipping unloaded adjacent chunks.
 */
@Mixin(value = SpreadingSnowyBlock.class, priority = 900)
public abstract class SpreadingSnowyBlockTightMixin extends SnowyBlock {
    @Shadow @Final
    private ResourceKey<Block> baseBlock;

    @Unique
    private Block neo$cachedBaseBlock;

    protected SpreadingSnowyBlockTightMixin(Properties properties) {
        super(properties);
    }

    @Unique
    private static boolean neo$canStayAliveFast(BlockState state, BlockState aboveState) {
        if (aboveState.is(Blocks.SNOW) && aboveState.getValue(SnowLayerBlock.LAYERS) == 1) {
            return true;
        }
        if (aboveState.getFluidState().isFull()) {
            return false;
        }
        return LightEngine.getLightDampeningInto(state, aboveState, Direction.UP, aboveState.getLightDampening()) < 15;
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void neo$fastOutsideRandomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!OutsideWorldOptimizer.enabled() || Neodymium2.regionProtectsBlock(level, pos)) {
            return;
        }
        Block base = this.neo$cachedBaseBlock;
        if (base == null) {
            var opt = level.registryAccess().lookupOrThrow(Registries.BLOCK).getOptional(this.baseBlock);
            if (opt.isEmpty()) {
                ci.cancel();
                return;
            }
            base = opt.get();
            this.neo$cachedBaseBlock = base;
        }
        int homeCx = pos.getX() >> 4;
        int homeCz = pos.getZ() >> 4;
        LevelChunk homeChunk = level.getChunkSource().getChunkNow(homeCx, homeCz);
        if (homeChunk == null) {
            ci.cancel();
            return;
        }
        BlockPos aboveSelf = pos.above();
        BlockState aboveSelfState = homeChunk.getBlockState(aboveSelf);
        if (!neo$canStayAliveFast(state, aboveSelfState)) {
            level.setBlockAndUpdate(pos, base.defaultBlockState());
            ci.cancel();
            return;
        }
        if (level.getMaxLocalRawBrightness(aboveSelf) >= 9) {
            BlockState defaultState = this.defaultBlockState();
            BlockPos.MutableBlockPos targetPos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos targetAbovePos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < 4; ++i) {
                int tx = pos.getX() + random.nextInt(3) - 1;
                int ty = pos.getY() + random.nextInt(5) - 3;
                int tz = pos.getZ() + random.nextInt(3) - 1;
                int tcx = tx >> 4;
                int tcz = tz >> 4;
                LevelChunk targetChunk = (tcx == homeCx && tcz == homeCz)
                    ? homeChunk
                    : level.getChunkSource().getChunkNow(tcx, tcz);
                if (targetChunk == null) {
                    continue;
                }
                targetPos.set(tx, ty, tz);
                if (targetChunk.getBlockState(targetPos).is(base)) {
                    targetAbovePos.set(tx, ty + 1, tz);
                    BlockState aboveTargetState = targetChunk.getBlockState(targetAbovePos);
                    if (neo$canStayAliveFast(defaultState, aboveTargetState) && !aboveTargetState.getFluidState().is(FluidTags.WATER)) {
                        level.setBlockAndUpdate(targetPos.immutable(), defaultState.setValue(SNOWY, isSnowySetting(aboveTargetState)));
                    }
                }
            }
        }
        ci.cancel();
    }
}
