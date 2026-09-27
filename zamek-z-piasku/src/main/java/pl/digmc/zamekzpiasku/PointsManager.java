package pl.digmc.zamekzpiasku;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Prosty, samodzielny system punktow uzywany do przyznawania bonusu za
 * zabojstwa w trakcie eventu "Zamek z Piasku".
 * <p>
 * Jesli w config.yml pod "rewards.external-points-command" podana jest
 * komenda, punkty NIE sa zapisywane wewnetrznie - zamiast tego wykonywana
 * jest ta komenda z konsoli, co pozwala podpiac dowolny istniejacy system
 * punktow/rankingu na serwerze.
 */
public class PointsManager {

    private final ZamekPlugin plugin;
    private final File storageFile;
    private final Map<UUID, Integer> points = new HashMap<>();

    public PointsManager(ZamekPlugin plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "points.yml");
        load();
    }

    private void load() {
        if (!storageFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(storageFile);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                points.put(uuid, yaml.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // wpis nie jest poprawnym UUID, pomijamy
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : points.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Nie udalo sie zapisac points.yml", e);
        }
    }

    /**
     * Dodaje graczowi bonusowe punkty za zabojstwo w trakcie eventu.
     * Uzywa zewnetrznej komendy, jesli jest skonfigurowana, w przeciwnym
     * razie korzysta z wewnetrznego magazynu punktow.
     */
    public void addBonus(Player player, int amount) {
        String externalCommand = plugin.getConfig().getString("rewards.external-points-command", "");
        if (externalCommand != null && !externalCommand.isBlank()) {
            String parsed = externalCommand
                    .replace("%player%", player.getName())
                    .replace("%amount%", String.valueOf(amount));
            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed));
            return;
        }

        UUID uuid = player.getUniqueId();
        points.merge(uuid, amount, Integer::sum);
    }

    public int getPoints(OfflinePlayer player) {
        return points.getOrDefault(player.getUniqueId(), 0);
    }
}
