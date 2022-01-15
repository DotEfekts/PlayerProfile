package net.dotefekts.playerprofile;

import com.mojang.authlib.GameProfile;
import net.dotefekts.dotutils.commandhelper.CommandEvent;
import net.dotefekts.dotutils.commandhelper.CommandHandler;
import net.dotefekts.dotutils.commandhelper.PermissionHandler;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ProfileCommand {
    private final SwapHandler swapHandler;
    private final SwapMessageHandler swapMessageHandler;

    public ProfileCommand (SwapHandler swapHandler, SwapMessageHandler swapMessageHandler) {
        this.swapHandler = swapHandler;
        this.swapMessageHandler = swapMessageHandler;
    }

    @CommandHandler(command = "profile",
            description = "Switch your current profile.",
            format = "s[Profile Name]")
    public boolean profileSwitchCommand(CommandEvent event) {
        var player = (Player) event.sender();

        if(event.args().length == 1) {
            if(!player.hasPermission("playerprofile.profile")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            var profile = swapHandler.getBaseProfile(player);
            var newProfile = generateUserProfile(profile, event.args()[0]);

            var newUUID = swapHandler.swapUserProfile(player, newProfile, true, true).getUUID();
            swapMessageHandler.queueMessage(newUUID, ChatColor.GREEN + "Switched to profile " + event.args()[0] + ".");
        } else {
            var baseProfile = swapHandler.getBaseProfile(player);
            if (baseProfile.getId() == player.getUniqueId()) {
                if(player.hasPermission("playerprofile.profile"))
                    player.sendMessage(ChatColor.YELLOW + "You are already on your standard profile.");
                else
                    player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            } else {
                var newUUID = swapHandler.swapUserProfile(player, baseProfile, true, true).getUUID();
                swapMessageHandler.queueMessage(newUUID, ChatColor.GREEN + "Restored standard profile.");
            }
        }

        return true;
    }

    public static GameProfile generateUserProfile(GameProfile accountProfile, String profileName) {
        var uuid = UUID.nameUUIDFromBytes((net.minecraft.world.entity.player.Player.UUID_PREFIX_OFFLINE_PLAYER + accountProfile.getName() + profileName).getBytes(StandardCharsets.UTF_8));
        var newProfile = new GameProfile(uuid, accountProfile.getName());
        for(var property : accountProfile.getProperties().values())
            newProfile.getProperties().put(property.getName(), property);

        return newProfile;
    }
}
