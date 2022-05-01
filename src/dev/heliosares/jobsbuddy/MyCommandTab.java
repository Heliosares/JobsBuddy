package dev.heliosares.jobsbuddy;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

public class MyCommandTab implements TabCompleter {

	@SuppressWarnings("unused")
	private final JobsBuddy plugin;

	public MyCommandTab(JobsBuddy plugin) {
		this.plugin = plugin;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String cmdLabel, String[] args) {
		List<String> output = new ArrayList<>();
		boolean anymatch = false;

		List<String> outputMatched = new ArrayList<>();

		if (anymatch) {
			for (String match : output) {
				if (match.toLowerCase().contains(args[args.length - 1].toLowerCase())) {
					outputMatched.add(match);
				}
			}
		} else {
			StringUtil.copyPartialMatches(args[args.length - 1], output, outputMatched);
		}
		return outputMatched;
	}

}
