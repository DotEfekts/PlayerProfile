package net.dotefekts.playerprofile;

import com.mojang.authlib.GameProfile;
import net.dotefekts.dotutils.commandhelper.CommandEvent;
import net.dotefekts.dotutils.commandhelper.CommandHandler;
import net.dotefekts.dotutils.commandhelper.PermissionHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_18_R1.CraftServer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

import java.util.Optional;
import java.util.function.Consumer;

public class SpoofCommand {
    private final SwapHandler swapHandler;
    private final SwapMessageHandler swapMessageHandler;

    public SpoofCommand(SwapHandler swapHandler, SwapMessageHandler swapMessageHandler) {
        this.swapHandler = swapHandler;
        this.swapMessageHandler = swapMessageHandler;
    }

    @CommandHandler(command = "spoof",
            description = "Spoof another players account.",
            format = "s[Account Name]")
    public boolean spoofCommand(CommandEvent event) {
        var args = event.args();
        var player = (Player) event.sender();

        if(args.length == 1) {
            if(!player.hasPermission("playerprofile.spoof")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            var server = ((CraftServer) Bukkit.getServer()).getServer();
            server.getProfileCache().getAsync(args[0], new ProfileFetchedConsumer(player));
            player.sendMessage(ChatColor.GREEN + "Fetching account for " + args[0] + ".");
        } else {
            var originalProfile = swapHandler.getOriginalProfile(player);
            if (originalProfile.getId() == player.getUniqueId()) {
                if (player.hasPermission("playerprofile.profile"))
                    player.sendMessage(ChatColor.YELLOW + "You are already on your own account.");
                else
                    player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            } else {
                var newUUID = swapHandler.revertUserSwap(player).getUUID();
                swapMessageHandler.queueMessage(newUUID, ChatColor.GREEN + "Restored standard account.");
            }
        }

        return true;
    }

    private class ProfileFetchedConsumer implements Consumer<Optional<GameProfile>> {
        private final Player player;

        public ProfileFetchedConsumer(Player player) {
            this.player = player;
        }

        @Override
        public void accept(Optional<GameProfile> gameProfile) {
            if(!player.isOnline())
                return;

            if(gameProfile.isEmpty()) {
                player.sendMessage(ChatColor.RED + "The account requested could not be retrieved.");
                return;
            }

            var server = ((CraftServer) Bukkit.getServer()).getServer();
            var filledProfile = server.getSessionService().fillProfileProperties(gameProfile.get(), true);

            if(filledProfile.getProperties().get("textures").isEmpty()) {
                player.sendMessage(ChatColor.RED + "Unable to fetch skin for account, please try again.");
                return;
            }

            swapHandler.swapUserProfile(player, filledProfile, false, false);
            swapMessageHandler.queueMessage(filledProfile.getId(), ChatColor.GREEN + "Switched to player " + filledProfile.getName() + ".");
        }
    }
}
