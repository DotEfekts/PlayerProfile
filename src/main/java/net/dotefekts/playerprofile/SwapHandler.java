package net.dotefekts.playerprofile;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.kyori.adventure.text.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_18_R1.CraftServer;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

public class SwapHandler implements Listener {
    private final ConnectionTracker connectionTracker;
    private final HashMap<UUID, String> userHostnames = new HashMap<>();
    private final HashSet<UUID> duplicateKickProtection = new HashSet<>();

    public SwapHandler() {
        this.connectionTracker = new ConnectionTracker();
    }

    public ServerPlayer swapUserProfile(Player player, GameProfile profile, boolean silent, boolean retainBaseProfile) {
        var playerHandle = ((CraftPlayer) player).getHandle();

        var currentProfile = playerHandle.getGameProfile();
        if(currentProfile.getId() == profile.getId() && currentProfile.getName().equals(profile.getName())) {
            player.sendMessage(ChatColor.YELLOW + "Current and swapped profile are the same, cancelling swap.");
            return playerHandle;
        }

        var server = ((CraftServer) Bukkit.getServer()).getServer();
        var connection = playerHandle.connection.connection;
        var connectionInfo = connectionTracker.get(playerHandle);

        connectionInfo.join(profile.getId());

        if(silent)
            connectionInfo.suppressNextLeave(true);
        playerHandle.connection.onDisconnect(new TextComponent("Profile swapped."));

        profile.getProperties().removeAll("originatingUUID");
        profile.getProperties().put("originatingUUID", new Property("originatingUUID", connectionInfo.getOriginalProfile().getId().toString()));

        ServerPlayer newPlayerHandle = server.getPlayerList().canPlayerLogin(
                new ServerLoginPacketListenerImpl(server, connection),
                profile, connectionInfo.getHostname());

        //noinspection ConstantConditions
        if(newPlayerHandle != null){
            connectionInfo.setProfile(profile, retainBaseProfile ? connectionInfo.getBaseProfile() : profile);
            if(silent)
                connectionInfo.suppressNextJoin(true);
            connectionInfo.setRequiresSetup();

            server.getPlayerList().placeNewPlayer(connection, newPlayerHandle);
            newPlayerHandle.connection.send(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, playerHandle));
            newPlayerHandle.connection.send(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, getTabListPlayer(server, newPlayerHandle)));

            return newPlayerHandle;
        } else {
            playerHandle.connection.connection.disconnect(new TextComponent("An error occurred while attempting connection swap."));
            return playerHandle;
        }
    }

    public ServerPlayer takeoverUser(Player player, Player targetPlayer) {
        var playerHandle = ((CraftPlayer) player).getHandle();

        if(player == targetPlayer) {
            player.sendMessage(ChatColor.YELLOW + "Current and target players are the same, cancelling takeover.");
            return playerHandle;
        }

        var targetHandle = ((CraftPlayer) targetPlayer).getHandle();

        var server = ((CraftServer) Bukkit.getServer()).getServer();
        var connection = playerHandle.connection.connection;
        var targetConnection = targetHandle.connection.connection;

        var connectionInfo = connectionTracker.get(playerHandle);
        var targetConnectionInfo = connectionTracker.get(targetHandle);

        connectionInfo.join(targetPlayer.getUniqueId());

        targetConnectionInfo.suppressNextLeave(true);

        targetHandle.connection.onDisconnect(new TextComponent("Takeover started."));

        var reason = new TextComponent("You were kicked from the server");
        targetConnection.send(new ClientboundDisconnectPacket(reason));
        targetConnection.disconnect(reason);

        targetHandle.getGameProfile().getProperties().removeAll("originatingUUID");
        targetHandle.getGameProfile().getProperties().put("originatingUUID", new Property("originatingUUID", connectionInfo.getOriginalProfile().getId().toString()));

        playerHandle.connection.onDisconnect(new TextComponent("Takeover started."));
        server.getPlayerList().placeNewPlayer(connection, targetHandle);
        duplicateKickProtection.add(targetHandle.getUUID());

        targetHandle.connection.send(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, playerHandle));
        targetHandle.connection.send(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, getTabListPlayer(server, targetHandle)));

        connectionInfo.setProfile(targetHandle.gameProfile, null);
        connectionInfo.setRequiresSetup();
        connectionInfo.suppressNextJoin(true);

        return targetHandle;
    }

    public ServerPlayer revertUserSwap(Player player) {
        var originalProfile = getOriginalProfile(player);
        if(originalProfile.getId() != player.getUniqueId() || originalProfile.getName().equals(player.getName())) {
            return swapUserProfile(player, originalProfile, false, false);
        }

        return ((CraftPlayer) player).getHandle();
    }

    public ConnectionTracker.ConnectionInfo getConnectionInfo(Player player) {
        return connectionTracker.get(player);
    }

    public GameProfile getOriginalProfile(Player player) {
        var playerHandle = ((CraftPlayer) player).getHandle();
        return getOriginalProfile(playerHandle);
    }

    public GameProfile getOriginalProfile(ServerPlayer playerHandle) {
        return connectionTracker.get(playerHandle).getOriginalProfile();
    }

    public GameProfile getBaseProfile(Player player) {
        var playerHandle = ((CraftPlayer) player).getHandle();
        return getBaseProfile(playerHandle);
    }

    public GameProfile getBaseProfile(ServerPlayer playerHandle) {
        return connectionTracker.get(playerHandle).getBaseProfile();
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if(duplicateKickProtection.contains(event.getPlayer().getUniqueId()))
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, Component.text("You cannot login right now"));
        else
            userHostnames.put(event.getPlayer().getUniqueId(), event.getHostname());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var playerHandle = ((CraftPlayer) event.getPlayer()).getHandle();
        var connectionInfo = connectionTracker.add(event.getPlayer());
        connectionInfo.trySetHostname(userHostnames.remove(playerHandle.getUUID()));

        if(connectionInfo.shouldSuppressJoin())
            event.joinMessage(null);

        if(connectionInfo.requiresSetup()) {
            var server = ((CraftServer) Bukkit.getServer()).getServer();
            var location = event.getPlayer().getLocation();

            server.getPlayerList().respawn(playerHandle, playerHandle.getLevel(), true, location, false);

            var tabListPlayer = getTabListPlayer(server, playerHandle);
            tabListPlayer.listName = new TextComponent("Swap Active").setStyle(Style.EMPTY.withItalic(true));

            playerHandle.connection.send(new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, tabListPlayer));
            playerHandle.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(new PlayerTeam(server.getScoreboard(), "0_swapMarker"), tabListPlayer.getScoreboardName(), ClientboundSetPlayerTeamPacket.Action.ADD));
        }
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        var connectionInfo = connectionTracker.get(event.getPlayer());

        if(connectionInfo.shouldSuppressLeave())
            event.quitMessage(null);

        duplicateKickProtection.remove(uuid);
        connectionTracker.leave(event.getPlayer());
    }

    private ServerPlayer getTabListPlayer(MinecraftServer server, ServerPlayer playerHandle) {
        var originalProfile = getOriginalProfile(playerHandle);
        var spoofProfile = playerHandle.getGameProfile();
        var clientSkinProfile = new GameProfile(originalProfile.getId(), "0_swapActive");

        for(var texture : spoofProfile.getProperties().get("textures"))
            clientSkinProfile.getProperties().put("textures", texture);

        return new ServerPlayer(server, Objects.requireNonNull(server.getLevel(Level.OVERWORLD)), clientSkinProfile);
    }
}
