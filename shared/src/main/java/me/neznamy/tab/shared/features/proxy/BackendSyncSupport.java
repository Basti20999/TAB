package me.neznamy.tab.shared.features.proxy;

import com.saicone.delivery4j.broker.RedisBroker;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.cpu.TimedCaughtTask;
import me.neznamy.tab.shared.features.belowname.BelowNameProxyPlayerData;
import me.neznamy.tab.shared.features.nametags.NameTagProxyPlayerData;
import me.neznamy.tab.shared.features.playerlist.PlayerListProxyPlayerData;
import me.neznamy.tab.shared.features.playerlistobjective.PlayerListObjectiveProxyPlayerData;
import me.neznamy.tab.shared.features.proxy.message.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

/** Direct backend transport. All roster state is owned by TAB's processing thread. */
public class BackendSyncSupport extends ProxyMessengerSupport {

    private final BackendRosterTracker roster = new BackendRosterTracker();
    private volatile boolean active;
    private volatile long lastReceived;

    public long secondsSinceLastMessage() {
        long received = lastReceived;
        return received == 0 ? -1 : TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - received);
    }

    public BackendSyncSupport(@NotNull ProxySupportConfiguration configuration) {
        // Never mix backend rosters with a proxy installation or VincentCore's channels.
        super("Redis backend sync", configuration.getChannelName() + "_backend_v1",
                () -> RedisBroker.of(configuration.getRedisUrl()));
        registerMessage(BackendRoster.class, BackendRoster::new);
    }

    @Override
    public void load() {
        active = true;
        super.load();
        TAB.getInstance().getCpu().getProcessingThread().repeatTask(new TimedCaughtTask(
                TAB.getInstance().getCpu(), this::heartbeat,
                getFeatureName(), TabConstants.CpuUsageCategory.PROXY_MESSAGE), 5000);
    }

    @Override
    public void unload() {
        synchronized (roster) {
            active = false;
        }
        super.unload();
        // Remove synthetic entries on /tab reload as well as plugin shutdown.
        synchronized (roster) {
            for (UUID id : new ArrayList<>(getProxyPlayers().keySet())) remove(id);
            roster.clear();
        }
    }

    @Override
    public void sendMessage(@NotNull ProxyMessage message) {
        // LoadRequest's response must include a complete roster, even when empty.
        super.sendMessage(message instanceof Load
                ? new BackendRoster(TAB.getInstance().getOnlinePlayers()) : message);
    }

    @Override
    protected void processIncoming(@NotNull String sender, @NotNull ProxyMessage message) {
        synchronized (roster) {
            receive(sender, message);
        }
    }

    private void heartbeat() {
        synchronized (roster) {
            if (!active) return;
            for (UUID id : roster.expire(System.nanoTime() - TimeUnit.SECONDS.toNanos(30))) remove(id);
            sendMessage(new BackendRoster(TAB.getInstance().getOnlinePlayers()));
            TAB.getInstance().getFeatureManager().onProxyLoadRequest();
        }
    }

    private void receive(String sender, ProxyMessage message) {
        if (!active) return;
        lastReceived = System.nanoTime();
        roster.heartbeat(sender, System.nanoTime());
        if (message instanceof BackendRoster) {
            Set<UUID> present = new HashSet<>();
            for (PlayerJoin join : ((BackendRoster) message).getPlayers()) {
                present.add(join.getUniqueId());
                accept(sender, join);
            }
            reconcile(sender, present);
        } else if (message instanceof PlayerJoin) {
            accept(sender, (PlayerJoin) message);
        } else if (message instanceof PlayerQuit) {
            UUID id = ((PlayerQuit) message).getPlayerId();
            // A late quit from the previous server must not remove the new connection.
            if (sender.equals(roster.owner(id))) remove(id);
        } else {
            UUID id = null;
            if (message instanceof UpdateVanishStatus) id = ((UpdateVanishStatus) message).getPlayerId();
            if (message instanceof PlayerListProxyPlayerData) id = ((PlayerListProxyPlayerData) message).getPlayerId();
            if (message instanceof NameTagProxyPlayerData) id = ((NameTagProxyPlayerData) message).getPlayerId();
            if (message instanceof BelowNameProxyPlayerData) id = ((BelowNameProxyPlayerData) message).getPlayerId();
            if (message instanceof PlayerListObjectiveProxyPlayerData) id = ((PlayerListObjectiveProxyPlayerData) message).getPlayerId();
            if (id != null && roster.owner(id) != null && !sender.equals(roster.owner(id))) return;
            super.processIncoming(sender, message);
        }
    }

    private void accept(String sender, PlayerJoin join) {
        UUID id = join.getUniqueId();
        ProxyPlayer existing = getProxyPlayers().get(id);
        if (existing != null && !sender.equals(roster.owner(id))) {
            remove(id);
            existing = null;
        }
        roster.claim(id, sender);
        if (existing == null) {
            join.process(this);
            // Queued formatting may have arrived before the join. Its default vanish flag
            // must not override the authoritative roster before the delayed display task.
            getProxyPlayers().get(id).setVanished(join.isVanished());
        } else if (existing.isVanished() != join.isVanished()) {
            new UpdateVanishStatus(id, join.isVanished()).process(this);
        }
    }

    private void reconcile(String sender, Set<UUID> present) {
        for (UUID id : roster.missing(sender, present)) remove(id);
    }

    private void remove(UUID id) {
        if (getProxyPlayers().containsKey(id)) new PlayerQuit(id).process(this);
        getQueuedData().remove(id);
        roster.remove(id);
    }
}
