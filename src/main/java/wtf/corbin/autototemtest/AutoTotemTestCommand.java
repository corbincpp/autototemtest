package wtf.corbin.autototemtest;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class AutoTotemTestCommand implements CommandExecutor, Listener {
    private final Main plugin = (Main) Main.getPlugin(Main.class);
    private final Set<Player> frozenPlayers = new HashSet<>();

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (commandSender instanceof Player) {
            final Player sender = (Player) commandSender;

            if (!sender.hasPermission("totemtest.use")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use this command");
                return true;
            }

            if (strings.length != 1) {
                sender.sendMessage(ChatColor.RED + "Incorrect Usage." + ChatColor.DARK_RED + " Use /totemtest <player>");
                return true;
            }

            final Player target = Bukkit.getPlayer(strings[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.DARK_RED + "Player not found");
                return true;
            }

            if (target.getGameMode() != GameMode.SURVIVAL && target.getGameMode() != GameMode.ADVENTURE) {
                sender.sendMessage(ChatColor.DARK_RED + target.getName() + " is not in survival or adventure mode");
                return true;
            }

            if (target.isInvulnerable() || target.isInvisible()) {
                sender.sendMessage(ChatColor.DARK_RED + target.getName() + " is invulnerable or invisible");
                return true;
            }

            if (target.getInventory().getItemInOffHand().getType() != Material.TOTEM_OF_UNDYING) {
                sender.sendMessage(ChatColor.DARK_RED + target.getName() + " does not have a totem in their offhand");
                return true;
            }

            boolean hasExtraTotems = false;
            for (int i = 9; i < 35; i++) {
                if (target.getInventory().getItem(i) != null && target.getInventory().getItem(i).getType() == Material.TOTEM_OF_UNDYING)
                    hasExtraTotems = true;
            }
            if (!hasExtraTotems) {
                sender.sendMessage(ChatColor.DARK_RED + target.getName() + " does not have any extra totems (hotbar totems don't count)");
                return true;
            }

            final boolean[] hotbar = new boolean[9];
            for (int j = 0; j < 9; j++) {
                ItemStack item = target.getInventory().getItem(j);
                if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                    hotbar[j] = true;
                    target.getInventory().setItem(j, null);
                }
            }

            final Block b = target.getLocation().getBlock();
            if (!b.isEmpty()) {
                sender.sendMessage(ChatColor.DARK_RED + target.getName() + " is not standing in an empty block");
                return true;
            }

            final boolean[] isCheating = {false};
            b.setType(Material.NETHER_PORTAL);
            target.damage(2000.0D);
            sender.sendMessage(ChatColor.GREEN + "Totem popped!");
            frozenPlayers.add(target);

            final RegionScheduler regionScheduler = plugin.getServer().getRegionScheduler();
            final Location portalLocation = b.getLocation().add(0.5, 0.0, 0.5);
            portalLocation.setYaw(0);
            portalLocation.setPitch(0);
            final Consumer<ScheduledTask> teleportTask = task -> target.teleportAsync(portalLocation);
            final Consumer<ScheduledTask> totemCheck = task -> {
                if (target.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING)
                    isCheating[0] = true;
            };
            final Consumer<ScheduledTask> finishTask = task -> {
                ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
                for (int i = 0; i < 9; i++) {
                    if (hotbar[i])
                        target.getInventory().setItem(i, totem);
                }
                b.setType(Material.AIR);
                frozenPlayers.remove(target);
                if (isCheating[0]) {
                    sender.sendMessage(ChatColor.RED + "Autototem test failed! " + ChatColor.DARK_RED + target.getName() + " is cheating");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Autototem test passed! " + ChatColor.DARK_GREEN + target.getName() + " is not cheating");
                    if (target.getInventory().getItem(EquipmentSlot.OFF_HAND) == null || target.getInventory().getItem(EquipmentSlot.OFF_HAND).getType() == Material.AIR) {
                        target.getInventory().setItem(EquipmentSlot.OFF_HAND, totem);
                        sender.sendMessage(ChatColor.DARK_GREEN + "Totem returned to player");
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "NOT returned to player");
                    }
                }
            };

            final ScheduledTask teleportScheduler = regionScheduler.runAtFixedRate(plugin, b.getLocation(), teleportTask, 1L, 1L);
            final ScheduledTask checkTask = regionScheduler.runAtFixedRate(plugin, b.getLocation(), totemCheck, 1L, 1L);
            regionScheduler.runDelayed(plugin, b.getLocation(), task -> {
                teleportScheduler.cancel();
                checkTask.cancel();
                finishTask.accept(task);
            }, 60L);
        } else {
            commandSender.sendMessage(ChatColor.DARK_RED + "Only players can use this command");
        }
        return true;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer())) {
            event.setTo(event.getFrom());
        }
    }
}
