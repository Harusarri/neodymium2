package kr.harusarri.neodymium2.core;

import net.minecraft.world.entity.Entity;
import java.util.function.Predicate;

public record TagSelectorPredicate(String tag, boolean invert) implements Predicate<Entity> {
    @Override
    public boolean test(Entity entity) {
        if ("".equals(this.tag)) {
            return entity.entityTags().isEmpty() != this.invert;
        }
        return entity.entityTags().contains(this.tag) != this.invert;
    }
}
