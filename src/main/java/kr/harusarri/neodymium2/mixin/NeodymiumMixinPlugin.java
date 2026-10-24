package kr.harusarri.neodymium2.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

/**
 * Guards optional third-party mod mixins so Neodymium 2 runs standalone on any Fabric + ServerCore server.
 */
public final class NeodymiumMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        FabricLoader loader = FabricLoader.getInstance();
        if (targetClassName.startsWith("de.z0rdak.yawp.")) {
            return loader.isModLoaded("yawp");
        }
        if (targetClassName.startsWith("carpet.fga.")) {
            return loader.isModLoaded("carpet-fga-addition");
        }
        if (targetClassName.startsWith("vectorwing.farmersdelight.")) {
            return loader.isModLoaded("farmersdelight");
        }
        if (targetClassName.startsWith("umpaz.brewinandchewin.")) {
            return loader.isModLoaded("brewinandchewin");
        }
        if (targetClassName.startsWith("carpettisaddition.")) {
            return loader.isModLoaded("carpet-tis-addition");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
