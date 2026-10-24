package kr.harusarri.neodymium2.core;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * Zero-allocation-when-empty Set<String> replacement for Entity.tags with:
 * 1. O(1) immediate false return when empty (99.5% of entities have 0 scoreboard tags).
 * 2. Bitflag fast-paths for hot tags ("exclude_ear", "exclude_lobotomization", "dreamdisplays_compat_hologram").
 * 3. Live synchronization with EntityLookup's inverted tag index (neo$byTag) on add/remove/clear.
 */
public final class FastEntityTagSet extends AbstractSet<String> {
    public static final int FLAG_EXCLUDE_EAR = 1;
    public static final int FLAG_EXCLUDE_LOBOTOMIZATION = 2;
    public static final int FLAG_DREAMDISPLAYS_HOLOGRAM = 4;

    private ObjectOpenHashSet<String> delegate;
    private int hotFlags;
    private EntityLookupTagAccess boundLookup;
    private int boundEntityId = -1;
    private Object boundEntity;

    public FastEntityTagSet(Set<String> initial) {
        if (initial != null && !initial.isEmpty()) {
            this.delegate = new ObjectOpenHashSet<>(initial.size());
            for (String tag : initial) {
                if (tag != null && this.delegate.add(tag)) {
                    this.hotFlags |= flagFor(tag);
                }
            }
        }
    }

    private static int flagFor(String tag) {
        return switch (tag) {
            case "exclude_ear" -> FLAG_EXCLUDE_EAR;
            case "exclude_lobotomization" -> FLAG_EXCLUDE_LOBOTOMIZATION;
            case "dreamdisplays_compat_hologram" -> FLAG_DREAMDISPLAYS_HOLOGRAM;
            default -> 0;
        };
    }

    public int hotFlags() {
        return this.hotFlags;
    }

    public boolean hasHotFlag(int mask) {
        return (this.hotFlags & mask) != 0;
    }

    public void bindLookup(EntityLookupTagAccess lookup, int entityId, Object entity) {
        if (this.boundLookup != null && this.boundLookup != lookup && this.delegate != null && !this.delegate.isEmpty()) {
            for (String tag : this.delegate) {
                this.boundLookup.neo$unindexEntityTag(tag, this.boundEntityId);
            }
        }
        this.boundLookup = lookup;
        this.boundEntityId = entityId;
        this.boundEntity = entity;
        if (lookup != null && this.delegate != null && !this.delegate.isEmpty()) {
            for (String tag : this.delegate) {
                lookup.neo$indexEntityTag(tag, entityId, entity);
            }
        }
    }

    public void unbindLookup(EntityLookupTagAccess lookup, int entityId) {
        if (this.boundLookup == lookup) {
            if (this.delegate != null && !this.delegate.isEmpty()) {
                for (String tag : this.delegate) {
                    lookup.neo$unindexEntityTag(tag, entityId);
                }
            }
            this.boundLookup = null;
            this.boundEntityId = -1;
            this.boundEntity = null;
        }
    }

    @Override
    public int size() {
        return this.delegate == null ? 0 : this.delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return this.delegate == null || this.delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        if (this.delegate == null || this.delegate.isEmpty() || !(o instanceof String s)) {
            return false;
        }
        int f = flagFor(s);
        if (f != 0) {
            return (this.hotFlags & f) != 0;
        }
        return this.delegate.contains(s);
    }

    @Override
    public boolean add(String tag) {
        if (tag == null) return false;
        if (this.delegate == null) {
            this.delegate = new ObjectOpenHashSet<>(4);
        }
        if (!this.delegate.add(tag)) {
            return false;
        }
        this.hotFlags |= flagFor(tag);
        if (this.boundLookup != null) {
            this.boundLookup.neo$indexEntityTag(tag, this.boundEntityId, this.boundEntity);
        }
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if (this.delegate == null || this.delegate.isEmpty() || !(o instanceof String tag)) {
            return false;
        }
        if (!this.delegate.remove(tag)) {
            return false;
        }
        this.hotFlags &= ~flagFor(tag);
        if (this.boundLookup != null) {
            this.boundLookup.neo$unindexEntityTag(tag, this.boundEntityId);
        }
        return true;
    }

    @Override
    public void clear() {
        if (this.delegate == null || this.delegate.isEmpty()) {
            return;
        }
        if (this.boundLookup != null) {
            for (String tag : this.delegate) {
                this.boundLookup.neo$unindexEntityTag(tag, this.boundEntityId);
            }
        }
        this.delegate.clear();
        this.hotFlags = 0;
    }

    @Override
    public Iterator<String> iterator() {
        if (this.delegate == null || this.delegate.isEmpty()) {
            return Collections.emptyIterator();
        }
        final Iterator<String> it = this.delegate.iterator();
        return new Iterator<>() {
            private String current;

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public String next() {
                this.current = it.next();
                return this.current;
            }

            @Override
            public void remove() {
                it.remove();
                if (this.current != null) {
                    FastEntityTagSet.this.hotFlags &= ~flagFor(this.current);
                    if (FastEntityTagSet.this.boundLookup != null) {
                        FastEntityTagSet.this.boundLookup.neo$unindexEntityTag(this.current, FastEntityTagSet.this.boundEntityId);
                    }
                    this.current = null;
                }
            }
        };
    }
}
