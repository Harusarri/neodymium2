package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.ProjectileCleanup;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
public abstract class ProjectileSweepMixin {
    // Chunk tickets have been updated; entity ticks and unload persistence have not run.
    @Inject(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/server/level/ServerChunkCache;tick(Ljava/util/function/BooleanSupplier;Z)V",shift=At.Shift.AFTER))
    private void neo$sweep(BooleanSupplier haveTime,CallbackInfo ci){ProjectileCleanup.sweep((ServerLevel)(Object)this);}
}
