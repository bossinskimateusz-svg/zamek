package pl.digmc.zamekzpiasku.commands;

import pl.digmc.zamekzpiasku.EventManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZamekCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("start", "stop", "build", "reload", "status");

    private final EventManager eventManager;

    public ZamekCommand(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("zamek.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnien do tej komendy.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uzycie: /zamek <start|stop|build|reload|status>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (eventManager.getState() != EventManager.State.IDLE) {
                    sender.sendMessage(ChatColor.RED + "Event juz trwa lub wlasnie sie zaczyna!");
                } else {
                    eventManager.startCountdown();
                    sender.sendMessage(ChatColor.GREEN + "Uruchomiono odliczanie do eventu 'Zamek z Piasku'.");
                }
            }
            case "stop" -> {
                eventManager.forceStop(true);
                sender.sendMessage(ChatColor.GREEN + "Event zostal zatrzymany, a teren przywrocony.");
            }
            case "build" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Ta podkomenda dziala tylko w grze (buduje zamek w Twojej lokalizacji).");
                    return true;
                }
                eventManager.buildPreviewOnly(player.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Zbudowano podglad zamku w Twojej lokalizacji (nie liczy sie jako event).");
            }
            case "reload" -> {
                sender.sendMessage(ChatColor.YELLOW + "Wskazowka: zmiany w config.yml wymagaja restartu/reloadu pluginu"
                        + " (np. komenda /reload lub PlugMan) - pelny hot-reload configu mozna dodac na zyczenie.");
            }
            case "status" -> sender.sendMessage(ChatColor.GOLD + "Stan eventu: " + ChatColor.WHITE + eventManager.getState());
            default -> sender.sendMessage(ChatColor.RED + "Nieznana podkomenda. Uzyj: /zamek <start|stop|build|reload|status>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        return List.of();
    }
}
