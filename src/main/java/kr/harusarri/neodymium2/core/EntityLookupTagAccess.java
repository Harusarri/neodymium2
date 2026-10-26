package kr.harusarri.neodymium2.core;

import java.util.Collection;

public interface EntityLookupTagAccess {
    void neo$indexEntityTag(String tag, int entityId, Object entity);
    void neo$unindexEntityTag(String tag, int entityId);
    boolean neo$hasEntitiesWithTag(String tag);
    Collection<?> neo$getMobValues();
}
