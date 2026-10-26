package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideEntityOptimizer;
import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import kr.harusarri.neodymium2.core.EntityFastStateAccess;
import kr.harusarri.neodymium2.core.FastEntityTagSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
import java.util.Set;

@Mixin(value = Entity.class, priority = 900)
public abstract class EntityTightMixin implements EntityFastStateAccess {
    @Shadow @Final @Mutable
    private Set<String> tags;

    @Shadow private boolean onGround;
    @Shadow public boolean horizontalCollision;
    @Shadow public boolean verticalCollision;
    @Shadow public boolean verticalCollisionBelow;
    @Shadow public boolean minorHorizontalCollision;
    @Shadow public Optional<BlockPos> mainSupportingBlockPos;
    @Shadow public boolean noPhysics;
    @Shadow public double fallDistance;
    @Shadow protected Vec3 stuckSpeedMultiplier;

    @Shadow public abstract Vec3 position();
    @Shadow public abstract BlockPos blockPosition();
    @Shadow public abstract Level level();
    @Shadow public abstract void setDeltaMovement(double x, double y, double z);

    @Unique private long neo$protGen = -1L;
    @Unique private AABB neo$protBb;
    @Unique private Level neo$protLevel;
    @Unique private boolean neo$protResult;

    @Unique private int neo$playerDistTick = Integer.MIN_VALUE;
    @Unique private double neo$playerDistSq = Double.MAX_VALUE;

    @Unique private long neo$yawpMobGriefGen = -1L;
    @Unique private long neo$yawpMobGriefPos = Long.MIN_VALUE;
    @Unique private int neo$yawpMobGriefUntilTick = -1;
    @Unique private boolean neo$yawpMobGriefCancelled;

    @Unique private long neo$selectorNbtTick = Long.MIN_VALUE;
    @Unique private ItemStack neo$selectorNbtSelectedItem;
    @Unique private CompoundTag neo$selectorNbtTag;
    @Unique private int neo$interactAwakeUntilTick = -1;

    @Unique private Vec3 neo$lastRestingPos;
    @Unique private BlockPos neo$lastSupportPos;
    @Unique private BlockState neo$lastSupportState;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neo$replaceTagSetWithFastIndexableSet(EntityType<?> type, Level level, CallbackInfo ci) {
        if (!(this.tags instanceof FastEntityTagSet)) {
            this.tags = new FastEntityTagSet(this.tags);
        }
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void neo$invalidateSelectorNbtCacheOnLoad(ValueInput input, CallbackInfo ci) {
        this.neo$selectorNbtTick = Long.MIN_VALUE;
        this.neo$selectorNbtTag = null;
        this.neo$lastRestingPos = null;
    }

    @Inject(method = "updateFluidInteraction", at = @At("HEAD"), cancellable = true)
    private void neo$skipStationaryOutsideFluidScan(CallbackInfoReturnable<Boolean> cir) {
        if (OutsideWorldOptimizer.shouldSkipOutsideFluidScan((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void neo$shortCircuitRestingGravityMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        if ((type == MoverType.PISTON || type == MoverType.SHULKER_BOX || type == MoverType.SHULKER)
            && (Object) this instanceof Mob pushedMob) {
            OutsideEntityOptimizer.wakeOnPlayerInteract(pushedMob);
        }
        if (type == MoverType.SELF
            && (Object) this instanceof Mob mob
            && this.onGround
            && !this.noPhysics
            && this.fallDistance <= 0.0
            && movement.x == 0.0
            && movement.z == 0.0
            && movement.y <= 0.0
            && movement.y >= -0.1
            && this.stuckSpeedMultiplier.lengthSqr() <= 1.0E-7
            && this.neo$lastRestingPos != null
            && this.position().equals(this.neo$lastRestingPos)
            && ((mob.getId() + mob.tickCount) & 7) != 0
            && OutsideWorldOptimizer.shouldShortCircuitRestingMove(mob, this.neo$lastSupportPos, this.neo$lastSupportState)) {
            this.horizontalCollision = false;
            boolean down = movement.y < 0.0;
            this.verticalCollision = down;
            this.verticalCollisionBelow = down;
            this.minorHorizontalCollision = false;
            if (down) {
                this.setDeltaMovement(0.0, 0.0, 0.0);
            }
            ci.cancel();
            return;
        }
        if (type == MoverType.SELF
            && (Object) this instanceof net.minecraft.world.entity.vehicle.boat.AbstractBoat boat
            && !this.noPhysics
            && !boat.isPassenger()
            && !boat.isVehicle()
            && Math.abs(movement.x) <= 1.0E-5
            && Math.abs(movement.z) <= 1.0E-5
            && Math.abs(movement.y) <= 0.05
            && ((boat.getId() + boat.tickCount) & 7) != 0
            && OutsideWorldOptimizer.enabled()
            && boat.level() instanceof net.minecraft.server.level.ServerLevel
            && !kr.harusarri.neodymium2.Neodymium2.regionProtects(boat)) {
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void neo$recordRestingGravitySupportState(MoverType type, Vec3 movement, CallbackInfo ci) {
        if (type == MoverType.SELF
            && (Object) this instanceof Mob
            && this.onGround
            && this.verticalCollisionBelow
            && !this.horizontalCollision
            && !this.noPhysics
            && this.mainSupportingBlockPos.isPresent()) {
            BlockPos supportPos = this.mainSupportingBlockPos.get();
            Level lvl = this.level();
            BlockState supportState = lvl.getBlockState(supportPos);
            BlockPos feetPos = this.blockPosition();
            BlockState feetState = feetPos.equals(supportPos) ? supportState : lvl.getBlockState(feetPos);
            if (!supportState.isAir()
                && !supportState.is(Blocks.MAGMA_BLOCK)
                && !supportState.is(Blocks.SLIME_BLOCK)
                && !supportState.is(Blocks.HONEY_BLOCK)
                && !supportState.is(Blocks.BUBBLE_COLUMN)
                && (feetState.isAir()
                    || feetPos.equals(supportPos)
                    || (feetState.getCollisionShape(lvl, feetPos).isEmpty()
                        && feetState.getFluidState().isEmpty()
                        && !feetState.is(Blocks.TRIPWIRE)
                        && !(feetState.getBlock() instanceof net.minecraft.world.level.block.BasePressurePlateBlock)
                        && !(feetState.getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock)))) {
                this.neo$lastRestingPos = this.position();
                this.neo$lastSupportPos = supportPos;
                this.neo$lastSupportState = supportState;
                return;
            }
        }
        this.neo$lastRestingPos = null;
    }

    @Override public long neo$getProtGen() { return this.neo$protGen; }
    @Override public AABB neo$getProtBb() { return this.neo$protBb; }
    @Override public Level neo$getProtLevel() { return this.neo$protLevel; }
    @Override public boolean neo$getProtResult() { return this.neo$protResult; }
    @Override
    public void neo$setProtCache(long gen, AABB bb, Level level, boolean result) {
        this.neo$protGen = gen;
        this.neo$protBb = bb;
        this.neo$protLevel = level;
        this.neo$protResult = result;
    }

    @Override public int neo$getPlayerDistTick() { return this.neo$playerDistTick; }
    @Override public double neo$getPlayerDistSq() { return this.neo$playerDistSq; }
    @Override
    public void neo$setPlayerDistCache(int tick, double distSq) {
        this.neo$playerDistTick = tick;
        this.neo$playerDistSq = distSq;
    }

    @Override public long neo$getYawpMobGriefGen() { return this.neo$yawpMobGriefGen; }
    @Override public long neo$getYawpMobGriefPos() { return this.neo$yawpMobGriefPos; }
    @Override public int neo$getYawpMobGriefUntilTick() { return this.neo$yawpMobGriefUntilTick; }
    @Override public boolean neo$isYawpMobGriefCancelled() { return this.neo$yawpMobGriefCancelled; }
    @Override
    public void neo$setYawpMobGriefCache(long gen, long posLong, int untilTick, boolean cancelled) {
        this.neo$yawpMobGriefGen = gen;
        this.neo$yawpMobGriefPos = posLong;
        this.neo$yawpMobGriefUntilTick = untilTick;
        this.neo$yawpMobGriefCancelled = cancelled;
    }

    @Override public long neo$getSelectorNbtTick() { return this.neo$selectorNbtTick; }
    @Override public ItemStack neo$getSelectorNbtSelectedItem() { return this.neo$selectorNbtSelectedItem; }
    @Override public CompoundTag neo$getSelectorNbtTag() { return this.neo$selectorNbtTag; }
    @Override
    public void neo$setSelectorNbtCache(long tick, ItemStack selectedItem, CompoundTag tag) {
        this.neo$selectorNbtTick = tick;
        this.neo$selectorNbtSelectedItem = selectedItem;
        this.neo$selectorNbtTag = tag;
    }

    @Override public int neo$getInteractAwakeUntilTick() { return this.neo$interactAwakeUntilTick; }
    @Override
    public void neo$setInteractAwakeUntilTick(int untilTick) {
        this.neo$interactAwakeUntilTick = untilTick;
    }
}
