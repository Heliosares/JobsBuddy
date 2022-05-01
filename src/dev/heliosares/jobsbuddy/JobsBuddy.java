package dev.heliosares.jobsbuddy;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class JobsBuddy extends JavaPlugin {
	public static long limitDuration;
	public MyListener listener;
	public static final String permissionNodePrefix = "jobsbuddy.";
	protected YMLManager data;
	private HashMap<String, Parser> limitEquations = new HashMap<>();
	// public NMS nms;

	@Override
	public void onEnable() {
		this.getConfig().options().copyDefaults(true);
		this.saveDefaultConfig();

		limitDuration = getConfig().getLong("limit-duration");

		ConfigurationSection limitequations = getConfig().getConfigurationSection("limit-equations");
		if (limitequations != null) {
			for (String key : limitequations.getKeys(false)) {
				limitEquations.put(key.toLowerCase(), new Parser(limitequations.getString(key)));
			}
		}

		Bukkit.getPluginManager().registerEvents((Listener) (listener = new MyListener(this)), this);

		this.getCommand("jobsbuddy").setExecutor(new MyCommand(this));
		this.getCommand("jobsbuddy").setTabCompleter(new MyCommandTab(this));

		data = new YMLManager("data", this);
		data.load();

		// TODO load file
		ConfigurationSection limits = data.getData().getConfigurationSection("Limits");
		if (limits != null) {
			for (String uuid : limits.getKeys(false)) {
				ConfigurationSection playerSection = limits.getConfigurationSection(uuid);
				if (playerSection != null) {
					JBPlayer jPlayer = this.getPlayer(Bukkit.getOfflinePlayer(UUID.fromString(uuid)));
					for (String job : playerSection.getKeys(false)) {
						long cooldown = data.getData().getLong("Limits." + uuid + "." + job + ".cooldown");
						if (cooldown < System.currentTimeMillis()) {
							continue;
						}
						double earned = data.getData().getDouble("Limits." + uuid + "." + job + ".earned");
						jPlayer.initJob(job, earned, cooldown);
					}
				}
			}
		}

		data.getFile().delete();
	}

	@Override
	public void onDisable() {
		data = new YMLManager("data", this);
		data.load();
		synchronized (players) {
			for (Entry<String, JBPlayer> entry : players.entrySet()) {
				for (String job : entry.getValue().cooldown.keySet()) {
					double earned = entry.getValue().getEarned(job);
					if (earned < 0.01) {
						continue;
					}
					long cooldown = entry.getValue().getCooldown(job);
					if (cooldown < System.currentTimeMillis()) {
						continue;
					}

					data.getData().set("Limits." + entry.getKey() + "." + job + ".cooldown", cooldown);
					data.getData().set("Limits." + entry.getKey() + "." + job + ".earned", earned);
				}
			}
		}
		data.save();
	}

	private HashMap<String, JBPlayer> players = new HashMap<>();

	public JBPlayer getPlayer(OfflinePlayer player) {
		synchronized (players) {
			JBPlayer jPlayer = players.get(player.getUniqueId().toString());
			if (jPlayer == null) {
				jPlayer = new JBPlayer(this, player);
				players.put(player.getUniqueId().toString(), jPlayer);
			}
			return jPlayer;
		}
	}

	public void removePlayer(String uuid) {
		synchronized (players) {
			players.remove(uuid);
		}
	}

	public static String formatMoney(double amount) {
		amount = Math.round(amount * 100) / 100.0;
		int cents = (int) (amount % 1 * 100);
		int dollars = (int) amount;
		if (cents == 0) {
			return "$" + dollars;
		}
		return "$" + dollars + "." + cents;
	}

	public static String formatTime(long millis, boolean color) {
		if (millis <= 0) {
			return "0 sec";
		}
		long hours = millis / 3600000L;
		millis -= hours * 3600000L;
		long minutes = millis / 60000L;
		millis -= minutes * 60000L;
		long seconds = millis / 1000L;
		millis -= seconds * 1000L;

		String output = "";
		if (hours > 0) {
			if (color) {
				output += "§e";
			}
			output += hours;
			if (color) {
				output += "§6";
			}
			output += " hrs";
		}
		if (minutes > 0) {
			if (color) {
				output += " §e";
			}
			output += minutes;
			if (color) {
				output += "§6";
			}
			output += " min";
		}
		if (seconds > 0) {
			if (color) {
				output += " §e";
			}
			output += seconds;
			if (color) {
				output += "§6";
			}
			output += " sec";
		}
		return output.trim();
	}

	public double getLimit(String job, int joblevel, int totallevel) {
		job = job.toLowerCase().replaceAll(" ", "");
		Parser parser = this.limitEquations.get(job);
		if (parser == null) {
			parser = this.limitEquations.get("global");
		}
		if (parser == null) {
			return -1;
		}
		try {
			parser.setVariable("joblevel", joblevel);
			parser.setVariable("totallevel", totallevel);
			return parser.parse();
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}
}
