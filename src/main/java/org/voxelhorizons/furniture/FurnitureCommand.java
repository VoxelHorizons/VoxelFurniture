package org.voxelhorizons.furniture;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.voxelhorizons.VoxelCore;
import org.voxelhorizons.content.ContentID;
import org.voxelhorizons.furniture.model.FurnitureDefinition;
import org.voxelhorizons.furniture.model.FurnitureInstance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FurnitureCommand implements CommandExecutor, TabCompleter {
    private final VoxelCore core;
    private final FurnitureManager furniture;

    public FurnitureCommand(VoxelCore core, FurnitureManager furniture) {
        this.core = core;
        this.furniture = furniture;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "VoxelFurniture " + ChatColor.GRAY + "- "
                    + furniture.definitions().size() + " definitions, " + furniture.instances().size() + " placed");
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " list | give <id> [amount] | remove <uuid> | cleanup");
            return true;
        }
        if ("list".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("voxelfurniture.admin.list")) return denied(sender);
            for (Map.Entry<ContentID, FurnitureDefinition> entry : furniture.definitions().entrySet()) {
                sender.sendMessage(ChatColor.AQUA + entry.getKey().toString() + ChatColor.GRAY + " ("
                        + entry.getValue().renderer().name().toLowerCase() + ")");
            }
            return true;
        }
        if ("give".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("voxelfurniture.admin.give")) return denied(sender);
            if (!(sender instanceof Player) || args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " give <id> [amount]");
                return true;
            }
            ContentID id = ContentID.parse(args[1], "minecraft");
            if (!furniture.definition(id).isPresent()) {
                sender.sendMessage(ChatColor.RED + "Unknown furniture: " + id);
                return true;
            }
            int amount;
            try {
                amount = args.length > 2 ? Integer.parseInt(args[2]) : 1;
            } catch (NumberFormatException exception) {
                sender.sendMessage(ChatColor.RED + "Amount must be a whole number.");
                return true;
            }
            if (amount < 1 || amount > 64) {
                sender.sendMessage(ChatColor.RED + "Amount must be between 1 and 64.");
                return true;
            }
            ((Player) sender).getInventory().addItem(core.getItemManager().createItem(id, amount));
            sender.sendMessage(ChatColor.GREEN + "Given " + amount + "x " + id);
            return true;
        }
        if ("cleanup".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("voxelfurniture.admin.cleanup")) return denied(sender);
            int removed = furniture.cleanupLoadedOrphans();
            sender.sendMessage(ChatColor.GREEN + "Removed " + removed
                    + " orphaned VoxelFurniture renderer entit" + (removed == 1 ? "y." : "ies."));
            sender.sendMessage(ChatColor.GRAY
                    + "Unloaded chunks are repaired automatically when they load.");
            return true;
        }

        if ("remove".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("voxelfurniture.admin.remove")) return denied(sender);
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /" + label + " remove <instance-uuid>");
                return true;
            }
            try {
                sender.sendMessage(furniture.remove(UUID.fromString(args[1]), false)
                        ? ChatColor.GREEN + "Furniture removed." : ChatColor.RED + "Unknown furniture instance.");
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(ChatColor.RED + "Invalid UUID.");
            }
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
        return true;
    }

    private static boolean denied(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You do not have permission.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(Arrays.asList("list", "give", "remove", "cleanup"), args[0]);
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<String>();
            for (ContentID id : furniture.definitions().keySet()) ids.add(id.toString());
            return filter(ids, args[1]);
        }
        if (args.length == 2 && "remove".equalsIgnoreCase(args[0])) {
            List<String> ids = new ArrayList<String>();
            for (FurnitureInstance instance : furniture.instances()) ids.add(instance.id().toString());
            return filter(ids, args[1]);
        }
        return Collections.emptyList();
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<String>();
        for (String value : values) if (value.toLowerCase().startsWith(prefix.toLowerCase())) result.add(value);
        return result;
    }
}
