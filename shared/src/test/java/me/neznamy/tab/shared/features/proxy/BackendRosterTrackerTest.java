package me.neznamy.tab.shared.features.proxy;

import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

public class BackendRosterTrackerTest {

    @Test
    public void emptyRosterRemovesOnlyPlayersStillOwnedByThatServer() {
        BackendRosterTracker tracker = new BackendRosterTracker();
        UUID moved = UUID.randomUUID();
        UUID disconnected = UUID.randomUUID();
        tracker.claim(moved, "eu");
        tracker.claim(disconnected, "eu");
        tracker.claim(moved, "usa");
        assertEquals(Collections.singleton(disconnected), tracker.missing("eu", Collections.emptySet()));
        assertEquals("usa", tracker.owner(moved));
        // A late EU quit can no longer pass the sender/owner check.
        assertNotEquals("eu", tracker.owner(moved));
    }

    @Test
    public void crashExpiresStalePeerWithoutRemovingTransferredPlayer() {
        BackendRosterTracker tracker = new BackendRosterTracker();
        UUID stale = UUID.randomUUID();
        UUID moved = UUID.randomUUID();
        tracker.heartbeat("eu", 100);
        tracker.heartbeat("usa", 200);
        tracker.claim(stale, "eu");
        tracker.claim(moved, "eu");
        tracker.claim(moved, "usa");
        assertEquals(Collections.singleton(stale), tracker.expire(150));
        tracker.remove(stale);
        assertTrue(tracker.expire(150).isEmpty());
        assertEquals("usa", tracker.owner(moved));
    }

    @Test
    public void heartbeatRenewsLeaseAndReconnectCanRebuildExpiredRoster() {
        BackendRosterTracker tracker = new BackendRosterTracker();
        UUID id = UUID.randomUUID();
        tracker.claim(id, "eu");
        tracker.heartbeat("eu", 100);
        tracker.heartbeat("eu", 200);
        assertTrue(tracker.expire(150).isEmpty());
        assertEquals(Collections.singleton(id), tracker.expire(250));
        tracker.remove(id);
        tracker.heartbeat("eu", 300);
        tracker.claim(id, "eu");
        assertTrue(tracker.missing("eu", Collections.singleton(id)).isEmpty());
        assertTrue(tracker.expire(250).isEmpty());
    }

    @Test
    public void reloadClearsAllLeasesAndOwners() {
        BackendRosterTracker tracker = new BackendRosterTracker();
        UUID id = UUID.randomUUID();
        tracker.claim(id, "eu");
        tracker.heartbeat("eu", 100);
        tracker.clear();
        assertNull(tracker.owner(id));
        assertTrue(tracker.expire(200).isEmpty());
    }
}
