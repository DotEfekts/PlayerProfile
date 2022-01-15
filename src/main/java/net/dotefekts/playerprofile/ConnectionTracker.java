package net.dotefekts.playerprofile;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.v1_18_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ConnectionTracker {
    private final HashMap<String, ConnectionInfo> connections = new HashMap<>();

    public ConnectionTracker() { }

    public ConnectionInfo add(Player player) {
        var playerHandle = ((CraftPlayer) player).getHandle();
        var connection = playerHandle.connection.connection;

        if(!connections.containsKey(connection.channel.id().asLongText()))
            connections.put(connection.channel.id().asLongText(), new ConnectionInfo(playerHandle.getGameProfile(), player.getEffectivePermissions()));

        var info = get(connection);
        info.join(playerHandle.getUUID());

        return info;
    }

    public ConnectionInfo get(Player player) { return get(((CraftPlayer) player).getHandle()); }
    public ConnectionInfo get(ServerPlayer playerHandle) { return get(playerHandle.connection.connection); }
    public ConnectionInfo get(Connection connection) { return connections.get(connection.channel.id().asLongText()); }

    public void leave(Player player) {
        var info = get(player);
        var connection = ((CraftPlayer) player).getHandle().connection.connection;
        if(info.leave(player.getUniqueId()) == 0)
            if(connection.channel.isOpen())
                connection.channel.closeFuture().addListener((ChannelFutureListener) future ->
                    connections.remove(connection.channel.id().asLongText()));
            else
                connections.remove(connection.channel.id().asLongText());
    }

    public static class ConnectionInfo {
        private final HashSet<UUID> playerUses = new HashSet<>();
        private final Set<PermissionAttachmentInfo> originalPermissions;
        private final GameProfile originalProfile;
        private GameProfile baseProfile;
        private String hostname;

        private boolean requiresSetup = false;
        private boolean suppressNextJoin = false;
        private boolean suppressNextLeave = false;

        public ConnectionInfo(GameProfile profile, Set<PermissionAttachmentInfo> originalPermissions){
            this.originalPermissions = originalPermissions;
            this.originalProfile = profile;
            this.baseProfile = profile;
            this.hostname = null;
        }

        public void join(UUID uuid) { playerUses.add(uuid); }
        public int leave(UUID uuid) {
            playerUses.remove(uuid);
            return playerUses.size();
        }

        public boolean hasPermission(String permission) {
            return originalPermissions.stream().anyMatch(permissionInfo -> permissionInfo.getPermission().equals(permission));
        }

        public Set<PermissionAttachmentInfo> matchPermission(String permissionRegex) {
            return originalPermissions.stream().filter(permissionInfo -> permissionInfo.getPermission().matches(permissionRegex)).collect(Collectors.toUnmodifiableSet());
        }

        public GameProfile getOriginalProfile() {
            return this.originalProfile;
        }

        public GameProfile getBaseProfile() {
            return this.baseProfile;
        }

        public void setProfile(GameProfile profile, @Nullable GameProfile baseProfile) {
            this.baseProfile = baseProfile != null ? baseProfile : profile;
        }

        public String getHostname() { return this.hostname; }

        public void trySetHostname(String hostname) { if(hostname != null) this.hostname = hostname; }

        public void setRequiresSetup() {
            this.requiresSetup = true;
        }

        public boolean requiresSetup() {
            var requiresSetup = this.requiresSetup;
            this.requiresSetup = false;
            return requiresSetup;
        }

        public void suppressNextJoin(boolean suppress) {
            this.suppressNextJoin = suppress;
        }

        public boolean shouldSuppressJoin() {
            var suppress = this.suppressNextJoin;
            this.suppressNextJoin =  false;
            return suppress;
        }

        public void suppressNextLeave(boolean suppress) {
            this.suppressNextLeave = suppress;
        }

        public boolean shouldSuppressLeave() {
            var suppress = this.suppressNextLeave;
            this.suppressNextLeave =  false;
            return suppress;
        }
    }
}