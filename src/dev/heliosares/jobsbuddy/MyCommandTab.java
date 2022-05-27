package dev.heliosares.jobsbuddy;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import com.dre.brewery.recipe.BRecipe;

import net.md_5.bungee.api.ChatColor;

public class MyCommandTab implements TabCompleter {

	@SuppressWarnings("unused")
	private final JobsBuddy plugin;

	public MyCommandTab(JobsBuddy plugin) {
		this.plugin = plugin;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String cmdLabel, String[] args) {
		List<String> output = new ArrayList<>();

		if (args.length <= 1) {
			if (MyPermission.ADMIN.hasPermission(sender)) {
				output.add("reload");
				output.add("debug");
				output.add("listbrews");
			}
		}
		if (args.length == 2) {
			if (args[0].equalsIgnoreCase("listbrews")) {
				if (MyPermission.ADMIN.hasPermission(sender)) {
					for (BRecipe recipe : BRecipe.getConfigRecipes()) {
						output.add(
								ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', recipe.getName(10))));
					}
				}
			}
		}

		List<String> outputMatched = new ArrayList<>();
		StringUtil.copyPartialMatches(args[args.length - 1], output, outputMatched);
		return outputMatched;
	}

}
