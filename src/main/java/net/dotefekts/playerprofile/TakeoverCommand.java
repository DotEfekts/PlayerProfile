package net.dotefekts.playerprofile;

import net.dotefekts.dotutils.DotUtilities;
import net.dotefekts.dotutils.commandhelper.CommandEvent;
import net.dotefekts.dotutils.commandhelper.CommandHandler;
import net.dotefekts.dotutils.commandhelper.PermissionHandler;
import net.dotefekts.dotutils.menuapi.InternalMenuException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.permissions.PermissionDefault;

public class TakeoverCommand {
    private final SwapHandler swapHandler;
    private final SwapMessageHandler swapMessageHandler;

    public TakeoverCommand(SwapHandler swapHandler, SwapMessageHandler swapMessageHandler) {
        this.swapHandler = swapHandler;
        this.swapMessageHandler = swapMessageHandler;
    }

    @PermissionHandler(node = "playerprofile.takeover",
            description = "Allows a user to take over an online player.",
            permissionDefault = PermissionDefault.OP)
    @CommandHandler(command = "takeover",
            description = "Take over an online players session.",
            format = "p[Player]")
    public boolean takeoverCommand(CommandEvent event) {
        var args = event.args();
        var player = (Player) event.sender();

        if(args.length == 1) {
            var targetPlayer = Bukkit.getPlayer(event.args()[0]);

            if(targetPlayer == null)
                player.sendMessage(ChatColor.RED + "The user to take over could not be found.");
            else
                performTakeover(player, targetPlayer);

            return true;
        } else {
            generatePlayerMenu(player, 1);
        }

        return true;
    }

    private void performTakeover(Player player, Player target) {
        var newUUID = swapHandler.takeoverUser(player, target).getUUID();
        swapMessageHandler.queueMessage(newUUID, ChatColor.GREEN + "Took over player " + target.getName() + ".");
    }

    private void generatePlayerMenu(Player player, int page) {
        var players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        var startAt = players.length > 45 ? Math.min(page * 36, players.length) : 0;
        var length = players.length > 45 ? 36 : players.length;

        var size = (int) Math.min(Math.ceil(players.length / 9f) * 9, 45);
        var menu = DotUtilities.getMenuManager().createMenu(player,  size, "Select player to takeover");

        for(int i = startAt; i < startAt + length; i++) {
            Player target = players[i];
            menu.setButton(getPlayerHead(players[i]), i, (playersMenu, menuButton) -> {
                playersMenu.markDestruction();
                performTakeover(player, target);
                return true;
            });
        }

        if(players.length > length) {
            if(startAt + length < players.length)
                menu.setButton(new ItemStack(Material.ARROW), 44, (playersMenu, menuButton) -> {
                    playersMenu.markDestruction();
                    generatePlayerMenu(player, (startAt / 36) + 1);
                    return true;
                });

            if(startAt != 0)
                menu.setButton(new ItemStack(Material.WHEAT), 38, (playersMenu, menuButton) -> {
                    playersMenu.markDestruction();
                    generatePlayerMenu(player, (startAt / 36) - 1);
                    return true;
                });
        }

        try {
            menu.showMenu();
        } catch (InternalMenuException e) {
            e.printStackTrace();
            player.sendMessage(ChatColor.RED + "Unable to show take over menu.");
        }
    }

    private ItemStack getPlayerHead(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(player.displayName());
        skull.setItemMeta(meta);

        return skull;
    }
}
