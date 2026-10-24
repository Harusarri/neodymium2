package kr.harusarri.neodymium2.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Mixin(value = SignBlockEntity.class, priority = 900)
public abstract class SignBlockEntityTightMixin extends BlockEntity {
    @Shadow private UUID playerWhoMayEdit;
    @Shadow public abstract boolean playerIsTooFarAwayToEdit(UUID uuid);

    protected SignBlockEntityTightMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "getPlayerWhoMayEdit", at = @At("HEAD"))
    private void neo$lazyClearFarSignEditor(CallbackInfoReturnable<UUID> cir) {
        UUID editor = this.playerWhoMayEdit;
        if (editor != null && this.level != null && this.playerIsTooFarAwayToEdit(editor)) {
            this.playerWhoMayEdit = null;
        }
    }
}
