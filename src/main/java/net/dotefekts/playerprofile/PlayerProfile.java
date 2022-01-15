package net.dotefekts.playerprofile;

import net.dotefekts.dotutils.DotUtilities;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("unused")
public class PlayerProfile extends JavaPlugin {

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        FileConfiguration config = this.getConfig();

        SwapHandler swapHandler = new SwapHandler();
        SwapMessageHandler swapMessageHandler = new SwapMessageHandler();

        this.getServer().getPluginManager().registerEvents(swapHandler, this);
        this.getServer().getPluginManager().registerEvents(swapMessageHandler, this);
        this.getServer().getPluginManager().registerEvents(new HostnameProfile(config.getString("hostname"), swapHandler, swapMessageHandler, this), this);

        DotUtilities.getCommandHelper().registerCommands(new ProfileCommand(swapHandler, swapMessageHandler), this);
        DotUtilities.getCommandHelper().registerCommands(new SpoofCommand(swapHandler, swapMessageHandler), this);
        DotUtilities.getCommandHelper().registerCommands(new TakeoverCommand(swapHandler, swapMessageHandler), this);
    }

    @Override
    public void onDisable() {

    }
}
