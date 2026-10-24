package kr.harusarri.neodymium2.core;

public interface EntityLookupTagAccess {
    void neo$indexEntityTag(String tag, int entityId, Object entity);
    void neo$unindexEntityTag(String tag, int entityId);
    boolean neo$hasEntitiesWithTag(String tag);
}
