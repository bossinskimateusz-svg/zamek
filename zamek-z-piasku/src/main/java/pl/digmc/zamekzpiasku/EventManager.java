package pl.digmc.zamekzpiasku;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class EventManager {

    public enum State {IDLE, COUNTDOWN, RUNNING}

    private final ZamekPlugin plugin;
    private final PointsManager pointsManager;
    private final StructureBuilder structureBuilder = new StructureBuilder();

    private State state = State.IDLE;
    private BossBar bossBar;
    private StructureBuilder.Snapshot currentStructure;
    private BukkitTask tickTask;
    private BukkitTask autoStartTask;
    private int secondsLeft;

    public EventManager(ZamekPlugin plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
    }

    public State getState() {
        return state;
    }

    /** Region budowli - uzywane do sprawdzenia czy dane zabojstwo bylo "w zamku". */
    public StructureBuilder.Region getActiveRegion() {
        return currentStructure == null ? null : currentStructure.region;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    /** Dolacza gracza do aktywnego bossbara, np. gdy dolacza do serwera w trakcie eventu. */
    public void addPlayerToActiveBossBar(Player player) {
        if (bossBar != null) {
            bossBar.addPlayer(player);
        }
    }

    // ---------------------------------------------------------------
    // Auto-start w tle, wg interval-minutes z configu
    // ---------------------------------------------------------------

    public void scheduleAutoStart() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean("event.auto-start", true)) {
            return;
        }
        long intervalTicks = cfg.getLong("event.interval-minutes", 30) * 60L * 20L;
        if (autoStartTask != null) {
            autoStartTask.cancel();
        }
        autoStartTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.IDLE) {
                return;
            }
            int minPlayers = cfg.getInt("event.min-players", 2);
            if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                return;
            }
            startCountdown();
        }, intervalTicks, intervalTicks);
    }

    // ---------------------------------------------------------------
    // Start / stop
    // ---------------------------------------------------------------

    /** Rozpoczyna zapowiedz (odliczanie) eventu; sam zamek stawiany jest po jej zakonczeniu. */
    public void startCountdown() {
        if (state != State.IDLE) {
            return;
        }
        state = State.COUNTDOWN;
        FileConfiguration cfg = plugin.getConfig();
        int countdown = Math.max(0, cfg.getInt("event.countdown-before-start", 10));
        String msgTemplate = msg("announce-countdown");

        if (countdown == 0) {
            beginEvent();
            return;
        }

        secondsLeft = countdown;
        broadcast(msgTemplate.replace("%time%", String.valueOf(secondsLeft)));

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            secondsLeft--;
            if (secondsLeft <= 0) {
                tickTask.cancel();
                beginEvent();
                return;
            }
            if (secondsLeft == 5 || secondsLeft == 10 || secondsLeft == 30 || secondsLeft == 60) {
                broadcast(msgTemplate.replace("%time%", String.valueOf(secondsLeft)));
            }
        }, 20L, 20L);
    }

    private void beginEvent() {
        FileConfiguration cfg = plugin.getConfig();

        World world = Bukkit.getWorld(cfg.getString("location.world", "world"));
        if (world == null) {
            plugin.getLogger().warning("Swiat z config.yml (location.world) nie istnieje - anuluje event.");
            state = State.IDLE;
            return;
        }
        Location center = new Location(world,
                cfg.getInt("location.x", 0),
                cfg.getInt("location.y", 100),
                cfg.getInt("location.z", 0));

        currentStructure = structureBuilder.build(center, cfg.getConfigurationSection("structure"));

        int duration = Math.max(10, cfg.getInt("event.duration-seconds", 90));
        secondsLeft = duration;
        state = State.RUNNING;

        bossBar = Bukkit.createBossBar(
                formatBossBarTitle(secondsLeft),
                barColor(cfg.getString("bossbar.color", "YELLOW")),
                barStyle(cfg.getString("bossbar.style", "SEGMENTED_10")));
        bossBar.setProgress(1.0);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        broadcast(msg("start").replace("%duration%", String.valueOf(duration)));

        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            secondsLeft--;
            bossBar.setTitle(formatBossBarTitle(secondsLeft));
            bossBar.setProgress(Math.max(0.0, (double) secondsLeft / duration));

            if (secondsLeft > 0 && secondsLeft % 30 == 0) {
                broadcast(msg("time-left").replace("%time%", String.valueOf(secondsLeft)));
            }

            if (secondsLeft <= 0) {
                endEvent();
            }
        }, 20L, 20L);
    }

    private void endEvent() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        broadcast(msg("end"));

        FileConfiguration cfg = plugin.getConfig();
        if (cfg.getBoolean("event.remove-structure-after", true) && currentStructure != null) {
            structureBuilder.remove(currentStructure);
        }
        currentStructure = null;
        state = State.IDLE;
    }

    /** Wymusza natychmiastowe zakonczenie/anulowanie eventu (np. przy /zamek stop lub wylaczaniu pluginu). */
    public void forceStop(boolean announce) {
        if (state == State.IDLE) {
            return;
        }
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (currentStructure != null) {
            structureBuilder.remove(currentStructure);
            currentStructure = null;
        }
        state = State.IDLE;
        if (announce) {
            broadcast(msg("end"));
        }
    }

    /** Buduje zamek natychmiast (bez odliczania) - przydatne do podgladu przez admina. */
    public StructureBuilder.Snapshot buildPreviewOnly(Location center) {
        return structureBuilder.build(center, plugin.getConfig().getConfigurationSection("structure"));
    }

    // ---------------------------------------------------------------
    // Bonus za zabojstwo w trakcie eventu
    // ---------------------------------------------------------------

    public void handleKillInsideEvent(Player killer) {
        int amount = plugin.getConfig().getInt("rewards.bonus-points-per-kill", 15);
        pointsManager.addBonus(killer, amount);
        killer.sendMessage(color(msg("kill-bonus").replace("%amount%", String.valueOf(amount))));
    }

    // ---------------------------------------------------------------
    // Pomocnicze
    // ---------------------------------------------------------------

    private String formatBossBarTitle(int seconds) {
        String template = plugin.getConfig().getString("bossbar.title", "&e%time%s");
        return color(template.replace("%time%", String.valueOf(Math.max(seconds, 0))));
    }

    private String msg(String key) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        return prefix + plugin.getConfig().getString("messages." + key, "");
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(color(message));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private BarColor barColor(String name) {
        try {
            return BarColor.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return BarColor.YELLOW;
        }
    }

    private BarStyle barStyle(String name) {
        try {
            return BarStyle.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return BarStyle.SEGMENTED_10;
        }
    }
}
