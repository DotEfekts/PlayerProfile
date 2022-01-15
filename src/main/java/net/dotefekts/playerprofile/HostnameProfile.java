package net.dotefekts.playerprofile;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.UUID;

public class HostnameProfile implements Listener {
    private final Plugin plugin;
    private final SwapHandler swapHandler;
    private final SwapMessageHandler swapMessageHandler;
    private final String[] hostname;
    private final HashMap<UUID, String> hostnameProfile = new HashMap<>();

    public HostnameProfile(String hostname, SwapHandler swapHandler, SwapMessageHandler swapMessageHandler, Plugin plugin) {
        this.swapHandler = swapHandler;
        this.swapMessageHandler = swapMessageHandler;
        this.plugin = plugin;

        if(hostname != null && hostname.contains("*")) {
            var hostnameSplit = hostname.split("\\*", -1);
            if(hostnameSplit.length == 2)
                this.hostname = new String[] { hostnameSplit[0], hostnameSplit[1] };
            else this.hostname = null;
        } else {
            this.hostname = null;
        }

        if(this.hostname != null)
            plugin.getLogger().info("Using " + hostname + " pattern for login profile detection.");
        else
            plugin.getLogger().info("Invalid or missing hostname pattern for login profile detection.");
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        var playerHostname =  event.getHostname().split(":")[0];
        var playerHandle = ((CraftPlayer) event.getPlayer()).getHandle();

        if(this.hostname != null &&
                event.getPlayer().hasPermission("playerprofile.profile.login") &&
                !playerHandle.getGameProfile().getProperties().containsKey("originatingUUID") &&
                playerHostname.startsWith(this.hostname[0]) && playerHostname.endsWith(this.hostname[1])) {
            playerHostname = playerHostname
                    .substring(0, playerHostname.length() - this.hostname[1].length())
                    .substring(this.hostname[0].length());
            if(playerHostname.length() > 0)
                hostnameProfile.put(playerHandle.getUUID(), playerHostname);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var playerHandle = ((CraftPlayer) event.getPlayer()).getHandle();
        var profileSwitch = hostnameProfile.remove(playerHandle.getUUID());
        if(profileSwitch != null) {
            var newProfile = ProfileCommand.generateUserProfile(playerHandle.getGameProfile(), profileSwitch);
            Bukkit.getScheduler().runTaskLater(plugin, () -> swapHandler.swapUserProfile(event.getPlayer(), newProfile, true, true), 1L);
            swapMessageHandler.queueMessage(newProfile.getId(), ChatColor.GREEN + "Switched to " + profileSwitch + " profile based on login hostname.");
        }
    }
}
