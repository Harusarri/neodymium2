package kr.harusarri.neodymium2.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import kr.harusarri.neodymium2.OutsideSpawnAndDensity;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import kr.harusarri.neodymium2.RegionSpawner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LocalMobCapCalculator;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

@Mixin(value = NaturalSpawner.class, priority = 900)
public abstract class NaturalSpawnerMixin {
    @WrapOperation(
        method = "createState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getAllEntities()Ljava/lang/Iterable;")
    )
    private static Iterable<Entity> neo$iterateOnlyMobsForCreateState(ServerLevel level, Operation<Iterable<Entity>> original) {
        if (!RegionSpawner.beginMobOnlyIteration(level)) {
            return original.call(level);
        }
        try {
            return level.getAllEntities();
        } finally {
            RegionSpawner.endMobOnlyIteration();
        }
    }

    @Inject(method = "lambda$createState$0", at = @At("HEAD"), cancellable = true)
    private static void neo$fastCreateStateAccept(
        ServerLevel level,
        BlockPos pos,
        Entity entity,
        PotentialCalculator spawnPotential,
        LocalMobCapCalculator localMobCapCalculator,
        MobCategory category,
        Object2IntOpenHashMap<MobCategory> mobCategoryCounts,
        LevelChunk chunk,
        CallbackInfo ci
    ) {
        if (RegionSpawner.tryFastAcceptCreateStateEntity(level, pos, entity, spawnPotential, localMobCapCalculator, category, mobCategoryCounts, chunk)) {
            ci.cancel();
        }
    }

    @WrapMethod(method = "spawnForChunk")
    private static void neo$regionAwareSpawn(ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState state, List<MobCategory> categories, Operation<Void> original) {
        if (!RegionSpawner.beginChunkSpawn(level, chunk)) return;
        try {
            List<MobCategory> filtered = RegionSpawner.filterOutsideCategories(level, categories);
            if (!filtered.isEmpty()) {
                original.call(level, chunk, state, filtered);
            }
        } finally {
            RegionSpawner.endChunkSpawn(chunk);
        }
    }

    @WrapOperation(
        method = "spawnCategoryForChunk(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;getRandomPosWithin(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/chunk/LevelChunk;)Lnet/minecraft/core/BlockPos;")
    )
    private static BlockPos neo$resolveOutsideSpawnPos(
        net.minecraft.world.level.Level level,
        LevelChunk chunk,
        Operation<BlockPos> original,
        MobCategory category
    ) {
        BlockPos pos = original.call(level, chunk);
        return RegionSpawner.resolveOutsideSpawnPos(level, chunk, category, pos);
    }

    @Inject(method = "isRightDistanceToPlayerAndSpawnPoint", at = @At("HEAD"), cancellable = true)
    private static void neo$preFilter3dDespawnSphere(ServerLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos, double distSqr, CallbackInfoReturnable<Boolean> cir) {
        if (RegionSpawner.shouldRejectOutsideSpawnPos(level, pos, distSqr)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mobsAt", at = @At("HEAD"), cancellable = true)
    private static void neo$shortCircuitEmptyStructureMobsAt(ServerLevel level, StructureManager structureManager, ChunkGenerator generator, MobCategory category, BlockPos pos, CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> cir) {
        var fast = OutsideWorldOptimizer.tryShortCircuitMobsAt(level, category, pos);
        if (fast != null) {
            cir.setReturnValue(fast);
        }
    }

    @WrapOperation(
        method = "isValidSpawnPostitionForType",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;noCollision(Lnet/minecraft/world/phys/AABB;)Z")
    )
    private static boolean neo$fastOutsideCollision(ServerLevel level, AABB bb, Operation<Boolean> original) {
        return OutsideSpawnAndDensity.doesNotCollide(level, bb, original);
    }

    @WrapOperation(
        method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/NaturalSpawner;getMobForSpawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/EntityType;)Lnet/minecraft/world/entity/Mob;")
    )
    private static Mob neo$precookedOutsideMob(ServerLevel level, EntityType<?> type, Operation<Mob> original) {
        return OutsideSpawnAndDensity.getMobForSpawn(level, type, original);
    }

    @Inject(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private static void neo$onSpawnSuccess(CallbackInfo ci) {
        RegionSpawner.onMobSpawned();
    }
}
