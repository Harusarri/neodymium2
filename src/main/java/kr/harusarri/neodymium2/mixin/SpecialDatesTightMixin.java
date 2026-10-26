package kr.harusarri.neodymium2.mixin;

import net.minecraft.util.SpecialDates;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.time.MonthDay;
import java.time.ZonedDateTime;

/**
 * Pufferfish 0007 / Purpur SpecialDates.dayNow() System Clock Cache:
 * Caches MonthDay.from(ZonedDateTime.now()) for 60 seconds so mob spawns (Zombie, Skeleton, etc.)
 * do not repeatedly query the OS clock and timezone rules on every spawn.
 */
@Mixin(value = SpecialDates.class, priority = 900)
public abstract class SpecialDatesTightMixin {
    @Unique
    private static final long NEO$CACHE_TTL_NANOS = 60_000_000_000L;

    @Unique
    private static volatile long neo$lastCheckNanos = Long.MIN_VALUE;

    @Unique
    private static volatile MonthDay neo$cachedDay;

    @Inject(method = "dayNow", at = @At("HEAD"), cancellable = true)
    private static void neo$cachedDayNow(CallbackInfoReturnable<MonthDay> cir) {
        long now = System.nanoTime();
        MonthDay cached = neo$cachedDay;
        if (cached != null && now - neo$lastCheckNanos < NEO$CACHE_TTL_NANOS) {
            cir.setReturnValue(cached);
            return;
        }
        MonthDay fresh = MonthDay.from(ZonedDateTime.now());
        neo$cachedDay = fresh;
        neo$lastCheckNanos = now;
        cir.setReturnValue(fresh);
    }
}
