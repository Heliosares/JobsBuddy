package dev.heliosares.jobsbuddy;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class MyCommand implements CommandExecutor {
	@SuppressWarnings("unused")
	private final JobsBuddy plugin;

	public MyCommand(JobsBuddy plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		return true;
	}
}
