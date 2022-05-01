package dev.heliosares.jobsbuddy;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public enum MyPermission {
	ADMIN("admin"), LIMIT("limit"), LIMIT_OTHERS("limit.others");

	public final String node;

	private MyPermission(String node) {
		this.node = JobsBuddy.permissionNodePrefix + node;
	}

	public boolean hasPermission(Player player) {
		return player.hasPermission(node);
	}

	public boolean hasPermission(CommandSender player) {
		return player.hasPermission(node);
	}
}
