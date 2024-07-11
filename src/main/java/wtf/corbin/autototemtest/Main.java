package wtf.corbin.autototemtest;

import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    public void onEnable() {
        getCommand("totemtest").setExecutor(new AutoTotemTestCommand());
    }

    public void onDisable() {}
}
