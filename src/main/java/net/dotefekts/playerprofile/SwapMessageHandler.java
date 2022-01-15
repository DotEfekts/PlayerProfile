package net.dotefekts.playerprofile;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;

public class SwapMessageHandler implements Listener {
    private final HashMap<UUID, List<String>> queuedMessages = new HashMap<>();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent joinEvent) {
        var player = joinEvent.getPlayer();
        var playerUUID = player.getUniqueId();

        if(queuedMessages.containsKey(playerUUID)) {
            for(var message : queuedMessages.get(playerUUID))
                player.sendMessage(message);
            queuedMessages.remove(playerUUID);
        }

    }

    public void queueMessage(UUID playerUUID, String message) {
        var player = Bukkit.getPlayer(playerUUID);
        if(player != null) {
            player.sendMessage(message);
        } else {
            if(!queuedMessages.containsKey(playerUUID))
                queuedMessages.put(playerUUID, new ArrayList<>());
            queuedMessages.get(playerUUID).add(message);
        }
    }
}
