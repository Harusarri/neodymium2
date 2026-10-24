package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.OutsideWorldOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import net.minecraft.world.level.block.entity.vault.VaultSharedData;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VaultBlockEntity.Server.class, priority = 900)
public abstract class VaultTightMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private static void neo$throttleOutsideVault(ServerLevel level, BlockPos pos, BlockState state, VaultConfig config, VaultServerData serverData, VaultSharedData sharedData, CallbackInfo ci) {
        if (OutsideWorldOptimizer.shouldThrottleOutsideTrialOrVault(level, pos)) {
            ci.cancel();
        }
    }
}
