package kr.harusarri.neodymium2.mixin;
import kr.harusarri.neodymium2.PoiChanges;
import kr.harusarri.neodymium2.core.PoiRevisionGrid;
import net.minecraft.core.*;
import net.minecraft.world.entity.ai.village.poi.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(PoiManager.class)
public abstract class PoiChangesMixin implements PoiChanges {
    @Unique private final PoiRevisionGrid neo$changes=new PoiRevisionGrid();
    public long neo$revisionNear(BlockPos pos){return neo$changes.near(pos.getX(),pos.getZ());}
    @Inject(method="add",at=@At("RETURN")) private void neo$add(BlockPos pos,Holder<PoiType> type,CallbackInfoReturnable<Boolean> ci){neo$changes.changed(pos.getX(),pos.getZ());}
    @Inject(method="remove",at=@At("RETURN")) private void neo$remove(BlockPos pos,CallbackInfo ci){neo$changes.changed(pos.getX(),pos.getZ());}
    @Inject(method="release",at=@At("RETURN")) private void neo$release(BlockPos pos,CallbackInfoReturnable<Boolean> ci){neo$changes.changed(pos.getX(),pos.getZ());}
}
