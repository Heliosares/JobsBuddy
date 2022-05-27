package dev.heliosares.jobsbuddy;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.dre.brewery.recipe.BRecipe;
import com.gamingmesh.jobs.container.CurrencyType;

import net.md_5.bungee.api.ChatColor;

public class MyCommand implements CommandExecutor {
	private final JobsBuddy plugin;

	public MyCommand(JobsBuddy plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if (args.length == 0 || args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("version")) {
			sender.sendMessage("§6JobsBuddy"
					+ (MyPermission.ADMIN.hasPermission(sender) ? (" §7v" + plugin.getDescription().getVersion())
							: ""));
			sender.sendMessage("§7Developed by §6Heliosares");
			return true;
		}
		if (args[0].equalsIgnoreCase("debug")) {
			if (!MyPermission.ADMIN.hasPermission(sender)) {
				sender.sendMessage("§cYou do not have permission for this command.");
				return true;
			}
			plugin.debug = !plugin.debug;
			plugin.getConfig().set("debug", plugin.debug);
			plugin.saveConfig();
			sender.sendMessage("Debug " + (plugin.debug ? "§aenabled." : "§cdisabled."));
			return true;
		} else if (args[0].equalsIgnoreCase("reload")) {
			if (!MyPermission.ADMIN.hasPermission(sender)) {
				sender.sendMessage("§cYou do not have permission for this command.");
				return true;
			}
			plugin.loadConfig();
			sender.sendMessage("§aConfig reloaded.");
		} else if (args[0].equalsIgnoreCase("listbrews")) {
			if (!MyPermission.ADMIN.hasPermission(sender)) {
				sender.sendMessage("§cYou do not have permission for this command.");
				return true;
			}
			String brew = "";
			if (args.length > 1) {
				for (int i = 1; i < args.length; i++) {
					brew += args[i] + " ";
				}
				brew = brew.trim();
			}
			boolean anything = false;
			for (BRecipe recipe : BRecipe.getConfigRecipes()) {
				String name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', recipe.getName(10)));
				if (brew.length() > 0 && !name.contains(brew)) {
					continue;
				}
				anything = true;
				double base = plugin.getValue(recipe, 10, CurrencyType.MONEY);
				String line = name + ": " + JobsBuddy.formatMoney(base);
				sender.sendMessage(line);
				if (sender instanceof Player) {
					plugin.debug(line);
				}
			}
			if (!anything) {
				sender.sendMessage("§cNo brew found by that name.");
			}
		}
		return true;
	}
}
