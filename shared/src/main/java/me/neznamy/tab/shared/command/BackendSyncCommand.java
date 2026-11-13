package me.neznamy.tab.shared.command;

import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants;
import me.neznamy.tab.shared.features.proxy.BackendSyncSupport;
import me.neznamy.tab.shared.features.proxy.ProxyPlayer;
import me.neznamy.tab.shared.features.proxy.ProxySupport;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import java.util.TreeMap;

/** Credential-free diagnostics usable from both backend consoles. */
public class BackendSyncCommand extends SubCommand {
    public BackendSyncCommand() {
        super("backend-sync", TabConstants.Permission.COMMAND_ALL);
    }

    @Override
    public void execute(@Nullable TabPlayer sender, @NotNull String[] args) {
        ProxySupport feature = TAB.getInstance().getFeatureManager().getFeature(TabConstants.Feature.PROXY_SUPPORT);
        if (!(feature instanceof BackendSyncSupport)) {
            sendMessage(sender, "&cBackend sync is not active. Check backend-sync.enabled and startup errors.");
            return;
        }
        BackendSyncSupport sync = (BackendSyncSupport) feature;
        String server = TAB.getInstance().getConfiguration().getConfig().getServerName();
        sendMessage(sender, "&aBackend sync status (large-payload fix v2)");
        sendMessage(sender, "&7Server: &f" + server + " &7| Channel: &f" + sync.getChannelName());
        sendMessage(sender, "&7Redis subscribed: &f" + sync.isRedisSubscribed());
        long age = sync.secondsSinceLastMessage();
        sendMessage(sender, "&7Last message from another backend: &f" + (age < 0 ? "never" : age + " seconds ago"));
        sendMessage(sender, "&7Local players: &f" + TAB.getInstance().getOnlinePlayers().length
                + " &7| Remote players: &f" + sync.getProxyPlayers().size());
        Map<String, Integer> counts = new TreeMap<>();
        for (ProxyPlayer player : sync.getProxyPlayers().values()) counts.merge(player.server.getName(), 1, Integer::sum);
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            sendMessage(sender, "&7Remote server: &f" + entry.getKey() + " &7| Players: &f" + entry.getValue());
            if (entry.getKey().equals(server)) sendMessage(sender, "&cDuplicate server-name: remote players are treated as local. Use a unique name on each backend.");
        }
        if (age < 0) sendMessage(sender, "&eNo peer messages received. Compare Redis endpoint, channel and backend-sync settings on both servers.");
    }
}
