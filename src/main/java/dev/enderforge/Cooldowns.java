package dev.enderforge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Cooldowns {
    private final Map<UUID, Map<String, Long>> map = new HashMap<>();

    /** Returns 0 and starts the cooldown if ready, otherwise the seconds left. */
    public long tryUse(UUID id, String key, int seconds) {
        long now = System.currentTimeMillis();
        Map<String, Long> m = map.computeIfAbsent(id, k -> new HashMap<>());
        long until = m.getOrDefault(key, 0L);
        if (until > now) return (until - now + 999) / 1000;
        m.put(key, now + seconds * 1000L);
        return 0;
    }
}
