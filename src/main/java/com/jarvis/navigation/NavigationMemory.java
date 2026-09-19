package com.jarvis.navigation;

import com.jarvis.world.LocatedStructure;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Remembers recent destinations so guidance can resume and "closest one" updates. */
public final class NavigationMemory {
    private final Deque<LocatedStructure> recent = new ArrayDeque<>();
    private final int cap;
    private LocatedStructure active;

    public NavigationMemory(int cap) {
        this.cap = cap;
    }

    public synchronized void setActive(LocatedStructure target) {
        this.active = target;
        recent.addLast(target);
        while (recent.size() > cap) recent.pollFirst();
    }

    public synchronized LocatedStructure active() {
        return active;
    }

    public synchronized void clearActive() {
        active = null;
    }

    public synchronized List<LocatedStructure> recent() {
        return new ArrayList<>(recent);
    }
}
