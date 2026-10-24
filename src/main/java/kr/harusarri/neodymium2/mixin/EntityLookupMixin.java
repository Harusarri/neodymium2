package kr.harusarri.neodymium2.mixin;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import kr.harusarri.neodymium2.RegionSpawner;
import kr.harusarri.neodymium2.core.EntityLookupTagAccess;
import kr.harusarri.neodymium2.core.FastEntityTagSet;
import kr.harusarri.neodymium2.core.ServerLevelTagAccess;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.IdentityHashMap;
import java.util.Map;

@Mixin(value = EntityLookup.class, priority = 900)
public abstract class EntityLookupMixin<T extends EntityAccess> implements EntityLookupTagAccess {
    @Unique
    private final Map<EntityType<?>, Int2ObjectLinkedOpenHashMap<T>> neo$byType = new IdentityHashMap<>();

    @Unique
    private final Int2ObjectLinkedOpenHashMap<T> neo$mobs = new Int2ObjectLinkedOpenHashMap<>();

    @Unique
    private final Map<String, Int2ObjectLinkedOpenHashMap<T>> neo$byTag = new Object2ObjectOpenHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public void neo$indexEntityTag(String tag, int entityId, Object entity) {
        this.neo$byTag.computeIfAbsent(tag, k -> new Int2ObjectLinkedOpenHashMap<>()).put(entityId, (T) entity);
        OutsideWorldOptimizer.bumpEntityTopologyRevision();
    }

    @Override
    public void neo$unindexEntityTag(String tag, int entityId) {
        var bucket = this.neo$byTag.get(tag);
        if (bucket != null) {
            bucket.remove(entityId);
            if (bucket.isEmpty()) {
                this.neo$byTag.remove(tag);
            }
        }
        OutsideWorldOptimizer.bumpEntityTopologyRevision();
    }

    @Override
    public boolean neo$hasEntitiesWithTag(String tag) {
        var bucket = this.neo$byTag.get(tag);
        return bucket != null && !bucket.isEmpty();
    }

    @Inject(
        method = "add",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;put(ILjava/lang/Object;)Ljava/lang/Object;",
            shift = At.Shift.AFTER
        )
    )
    private void neo$indexAddedEntity(T entity, CallbackInfo ci) {
        if (entity instanceof Entity mcEntity) {
            int id = mcEntity.getId();
            this.neo$byType.computeIfAbsent(mcEntity.getType(), k -> new Int2ObjectLinkedOpenHashMap<>()).put(id, entity);
            if (mcEntity instanceof Mob) {
                this.neo$mobs.put(id, entity);
            }
            if (mcEntity.level() instanceof ServerLevelTagAccess levelAccess && levelAccess.neo$getEntityLookupTagAccess() == null) {
                levelAccess.neo$setEntityLookupTagAccess(this);
            }
            var tags = mcEntity.entityTags();
            if (tags instanceof FastEntityTagSet fastTags) {
                fastTags.bindLookup(this, id, entity);
            } else if (!tags.isEmpty()) {
                for (String tag : tags) {
                    this.neo$indexEntityTag(tag, id, entity);
                }
            }
            OutsideWorldOptimizer.bumpEntityTopologyRevision();
        }
    }

    @Inject(method = "remove", at = @At("TAIL"))
    private void neo$unindexRemovedEntity(T entity, CallbackInfo ci) {
        if (entity instanceof Entity mcEntity) {
            int id = mcEntity.getId();
            var bucket = this.neo$byType.get(mcEntity.getType());
            if (bucket != null) {
                bucket.remove(id);
            }
            if (mcEntity instanceof Mob) {
                this.neo$mobs.remove(id);
            }
            var tags = mcEntity.entityTags();
            if (tags instanceof FastEntityTagSet fastTags) {
                fastTags.unbindLookup(this, id);
            } else if (!tags.isEmpty()) {
                for (String tag : tags) {
                    this.neo$unindexEntityTag(tag, id);
                }
            }
            OutsideWorldOptimizer.bumpEntityTopologyRevision();
        }
    }

    @Inject(method = "getEntities", at = @At("HEAD"), cancellable = true)
    private <U extends T> void neo$fastGetEntitiesByType(EntityTypeTest<T, U> typeTest, AbortableIterationConsumer<U> consumer, CallbackInfo ci) {
        if (!OutsideWorldOptimizer.enabled()) {
            return;
        }
        String reqTag = OutsideWorldOptimizer.currentRequiredSelectorTag();
        if (reqTag != null) {
            var tagBucket = this.neo$byTag.get(reqTag);
            if (tagBucket == null || tagBucket.isEmpty()) {
                OutsideWorldOptimizer.recordFastTagLookup();
                if (typeTest instanceof EntityType<?>) {
                    OutsideWorldOptimizer.recordFastTypeLookup();
                }
                ci.cancel();
                return;
            }
            if (!(typeTest instanceof EntityType<?>) || tagBucket.size() <= 4) {
                OutsideWorldOptimizer.recordFastTagLookup();
                if (typeTest instanceof EntityType<?>) {
                    OutsideWorldOptimizer.recordFastTypeLookup();
                }
                for (T entity : tagBucket.values()) {
                    U cast = typeTest.tryCast(entity);
                    if (cast != null && consumer.accept(cast).shouldAbort()) {
                        break;
                    }
                }
                ci.cancel();
                return;
            }
        }
        if (typeTest instanceof EntityType<?> type) {
            OutsideWorldOptimizer.recordFastTypeLookup();
            var bucket = this.neo$byType.get(type);
            if (bucket != null && !bucket.isEmpty()) {
                for (T entity : bucket.values()) {
                    U cast = typeTest.tryCast(entity);
                    if (cast != null && consumer.accept(cast).shouldAbort()) {
                        break;
                    }
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "getAllEntities", at = @At("HEAD"), cancellable = true)
    private void neo$getMobsOnlyForCreateState(CallbackInfoReturnable<Iterable<T>> cir) {
        if (RegionSpawner.isMobOnlyIteration()) {
            RegionSpawner.recordMobOnlyCreateStateScan();
            cir.setReturnValue(Iterables.unmodifiableIterable(this.neo$mobs.values()));
        }
    }
}
