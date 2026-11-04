package me.neznamy.tab.shared.features.proxy;

import java.util.*;

/** Tracks connection ownership independently of display data and network I/O. */
final class BackendRosterTracker {
    private final Map<UUID, String> owners = new HashMap<>();
    private final Map<String, Long> lastSeen = new HashMap<>();

    void heartbeat(String sender, long now) {
        lastSeen.put(sender, now);
    }

    String owner(UUID id) {
        return owners.get(id);
    }

    void claim(UUID id, String sender) {
        owners.put(id, sender);
    }

    void remove(UUID id) {
        owners.remove(id);
    }

    Set<UUID> missing(String sender, Set<UUID> present) {
        Set<UUID> result = new HashSet<>();
        owners.forEach((id, owner) -> {
            if (sender.equals(owner) && !present.contains(id)) result.add(id);
        });
        return result;
    }

    Set<UUID> expire(long cutoff) {
        Set<UUID> result = new HashSet<>();
        Iterator<Map.Entry<String, Long>> peers = lastSeen.entrySet().iterator();
        while (peers.hasNext()) {
            Map.Entry<String, Long> peer = peers.next();
            if (peer.getValue() < cutoff) {
                result.addAll(missing(peer.getKey(), Collections.emptySet()));
                peers.remove();
            }
        }
        return result;
    }

    void clear() {
        owners.clear();
        lastSeen.clear();
    }
}
