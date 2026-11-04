package me.neznamy.tab.shared.features.proxy.message;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import lombok.Getter;
import me.neznamy.tab.shared.features.proxy.ProxySupport;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Complete roster, including empty servers, used to repair missed join/quit messages. */
@Getter
public class BackendRoster extends ProxyMessage {

    private final List<PlayerJoin> players = new ArrayList<>();

    public BackendRoster(@NotNull TabPlayer[] online) {
        for (TabPlayer player : online) {
            if (player.isOnline()) players.add(new PlayerJoin(player));
        }
    }

    public BackendRoster(@NotNull ByteArrayDataInput in) {
        int count = in.readInt();
        if (count < 0 || count > 100000) throw new IllegalArgumentException("Invalid backend roster size");
        for (int i = 0; i < count; i++) players.add(new PlayerJoin(in));
    }

    @Override
    public void write(@NotNull ByteArrayDataOutput out) {
        out.writeInt(players.size());
        for (PlayerJoin player : players) player.write(out);
    }

    @Override
    public void process(@NotNull ProxySupport support) {
        // BackendSyncSupport reconciles this message together with its sender identity.
    }
}
