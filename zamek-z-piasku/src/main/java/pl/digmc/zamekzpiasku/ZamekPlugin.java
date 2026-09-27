package pl.digmc.zamekzpiasku;

import pl.digmc.zamekzpiasku.commands.ZamekCommand;
import pl.digmc.zamekzpiasku.listeners.CombatListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZamekPlugin extends JavaPlugin {

    private EventManager eventManager;
    private PointsManager pointsManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.pointsManager = new PointsManager(this);
        this.eventManager = new EventManager(this, pointsManager);

        getServer().getPluginManager().registerEvents(new CombatListener(eventManager), this);

        ZamekCommand commandExecutor = new ZamekCommand(eventManager);
        getCommand("zamek").setExecutor(commandExecutor);
        getCommand("zamek").setTabCompleter(commandExecutor);

        eventManager.scheduleAutoStart();

        getLogger().info("ZamekZPiasku wlaczony poprawnie.");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.forceStop(false);
        }
        if (pointsManager != null) {
            pointsManager.save();
        }
        getLogger().info("ZamekZPiasku wylaczony.");
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public PointsManager getPointsManager() {
        return pointsManager;
    }
}
