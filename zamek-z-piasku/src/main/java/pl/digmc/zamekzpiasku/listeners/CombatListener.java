package pl.digmc.zamekzpiasku.listeners;

import pl.digmc.zamekzpiasku.EventManager;
import pl.digmc.zamekzpiasku.StructureBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class CombatListener implements Listener {

    private final EventManager eventManager;

    public CombatListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!eventManager.isRunning()) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }

        StructureBuilder.Region region = eventManager.getActiveRegion();
        if (region == null) {
            return;
        }

        // Bonus liczy sie tylko, jesli zarowno ofiara jak i zabojca byli wewnatrz zamku
        if (region.contains(victim.getLocation()) && region.contains(killer.getLocation())) {
            eventManager.handleKillInsideEvent(killer);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        eventManager.addPlayerToActiveBossBar(event.getPlayer());
    }
}
