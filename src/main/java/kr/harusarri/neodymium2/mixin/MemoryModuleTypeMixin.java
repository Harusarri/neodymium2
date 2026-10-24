package kr.harusarri.neodymium2.mixin;

import kr.harusarri.neodymium2.core.MemoryTypeIndexAccess;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = MemoryModuleType.class, priority = 900)
public abstract class MemoryModuleTypeMixin implements MemoryTypeIndexAccess {
    @Unique
    private static final AtomicInteger NEO$NEXT_INDEX = new AtomicInteger(0);

    @Unique
    private int neo$index = -1;

    @Override
    public int neo$getMemoryIndex() {
        int idx = this.neo$index;
        if (idx < 0) {
            idx = NEO$NEXT_INDEX.getAndIncrement();
            this.neo$index = idx;
        }
        return idx;
    }
}
