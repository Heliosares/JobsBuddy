package dev.heliosares.jobsbuddy;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.api.JobsExpGainEvent;
import com.gamingmesh.jobs.api.JobsPrePaymentEvent;
import com.gamingmesh.jobs.container.Boost;
import com.gamingmesh.jobs.container.CurrencyLimit;
import com.gamingmesh.jobs.container.CurrencyType;
import com.gamingmesh.jobs.container.FastPayment;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.gamingmesh.jobs.economy.BufferedPayment;

public class JobsBuddy extends JavaPlugin {
	public static long limitDuration;
	public MyListener listener;
	public static final String permissionNodePrefix = "jobsbuddy.";
	protected YMLManager data;
	private HashMap<String, Parser> limitEquations = new HashMap<>();
	private HashMap<String, HashMap<String, Reward>> breweryRewards = new HashMap<>();
	protected boolean perJobLimit;
	// public NMS nms;

	@Override
	public void onEnable() {
		this.getConfig().options().copyDefaults(true);
		this.saveDefaultConfig();

		limitDuration = getConfig().getLong("limit-duration");
		perJobLimit = getConfig().getBoolean("per-job-limit");

		ConfigurationSection limitequations = getConfig().getConfigurationSection("limit-equations");
		if (limitequations != null) {
			for (String key : limitequations.getKeys(false)) {
				limitEquations.put(key.toLowerCase(), new Parser(limitequations.getString(key)));
			}
		}

		ConfigurationSection breweryJobs = getConfig().getConfigurationSection("Brewery");
		if (breweryJobs != null) {
			for (String job : breweryJobs.getKeys(false)) {
				ConfigurationSection brews = breweryJobs.getConfigurationSection(job);
				HashMap<String, Reward> rewards = new HashMap<>();
				for (String brewName : brews.getKeys(false)) {
					double income = brews.getDouble(brewName + ".income");
					double points = brews.getDouble(brewName + ".points");
					double experience = brews.getDouble(brewName + ".experience");

					Reward reward = new Reward(income, points, experience);
					rewards.put(brewName, reward);
				}
				this.breweryRewards.put(job, rewards);
			}
		}

		Bukkit.getPluginManager().registerEvents(listener = new MyListener(this), this);

		if (this.getServer().getPluginManager().getPlugin("Brewery") != null) {
			Bukkit.getPluginManager().registerEvents(new BreweryListener(this, breweryRewards), this);
			getLogger().info("Hooked into Brewery");
		}

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

	public void giveReward(JobsPlayer jPlayer, JobProgression prog, double income, double pointAmount,
			double expAmount) {
		Boost boost = Jobs.getPlayerManager().getFinalBonus(jPlayer, prog.getJob(), null, null);
		JobsPrePaymentEvent jobsPrePaymentEvent = new JobsPrePaymentEvent(jPlayer.getPlayer(), prog.getJob(), income,
				pointAmount, null, null, null, null);

		Bukkit.getServer().getPluginManager().callEvent(jobsPrePaymentEvent);
		// If event is canceled, don't do anything
		if (jobsPrePaymentEvent.isCancelled()) {
			income = 0D;
			pointAmount = 0D;
		} else {
			income = jobsPrePaymentEvent.getAmount();
			pointAmount = jobsPrePaymentEvent.getPoints();
		}

		// Calculate income
		if (income != 0D) {
			income = boost.getFinalAmount(CurrencyType.MONEY, income);

			if (Jobs.getGCManager().useMinimumOveralPayment && income > 0) {
				double maxLimit = income * Jobs.getGCManager().MinimumOveralPaymentLimit;

				if (income < maxLimit)
					income = maxLimit;
			}
		}

		// Calculate points
		if (pointAmount != 0D) {
			pointAmount = boost.getFinalAmount(CurrencyType.POINTS, pointAmount);

			if (Jobs.getGCManager().useMinimumOveralPoints && pointAmount > 0) {
				double maxLimit = pointAmount * Jobs.getGCManager().MinimumOveralPointsLimit;

				if (pointAmount < maxLimit)
					pointAmount = maxLimit;
			}
		}

		// Calculate exp
		if (expAmount != 0D) {
			expAmount = boost.getFinalAmount(CurrencyType.EXP, expAmount);

			if (Jobs.getGCManager().useMinimumOveralExp && expAmount > 0) {
				double maxLimit = expAmount * Jobs.getGCManager().minimumOveralExpLimit;

				if (expAmount < maxLimit)
					expAmount = maxLimit;
			}
		}

		if (!jPlayer.isUnderLimit(CurrencyType.MONEY, income)) {
			income = 0D;

			CurrencyLimit cLimit = Jobs.getGCManager().getLimit(CurrencyType.MONEY);

			if (cLimit.getStopWith().contains(CurrencyType.EXP))
				expAmount = 0D;

			if (cLimit.getStopWith().contains(CurrencyType.POINTS))
				pointAmount = 0D;
		}

		if (!jPlayer.isUnderLimit(CurrencyType.EXP, expAmount)) {
			expAmount = 0D;

			CurrencyLimit cLimit = Jobs.getGCManager().getLimit(CurrencyType.EXP);

			if (cLimit.getStopWith().contains(CurrencyType.MONEY))
				income = 0D;

			if (cLimit.getStopWith().contains(CurrencyType.POINTS))
				pointAmount = 0D;
		}

		if (!jPlayer.isUnderLimit(CurrencyType.POINTS, pointAmount)) {
			pointAmount = 0D;

			CurrencyLimit cLimit = Jobs.getGCManager().getLimit(CurrencyType.POINTS);

			if (cLimit.getStopWith().contains(CurrencyType.MONEY))
				income = 0D;

			if (cLimit.getStopWith().contains(CurrencyType.EXP))
				expAmount = 0D;
		}

		if (income == 0D && pointAmount == 0D && expAmount == 0D)
			return;

		// JobsPayment event
		JobsExpGainEvent jobsExpGainEvent = new JobsExpGainEvent(jPlayer.getPlayer(), prog.getJob(), expAmount, null,
				null, null, null);
		Bukkit.getServer().getPluginManager().callEvent(jobsExpGainEvent);
		// If event is canceled, don't do anything
		expAmount = jobsExpGainEvent.isCancelled() ? 0D : jobsExpGainEvent.getExp();

		try {
			if (expAmount != 0D && Jobs.getGCManager().BossBarEnabled)
				if (Jobs.getGCManager().BossBarShowOnEachAction)
					Jobs.getBBManager().ShowJobProgression(jPlayer, prog, expAmount);
				else
					jPlayer.getUpdateBossBarFor().add(prog.getJob().getName());
		} catch (Throwable e) {
			e.printStackTrace();
		}

		Map<CurrencyType, Double> payments = new HashMap<>();
		if (income != 0D)
			payments.put(CurrencyType.MONEY, income);
		if (pointAmount != 0D)
			payments.put(CurrencyType.POINTS, pointAmount);
		if (expAmount != 0D)
			payments.put(CurrencyType.EXP, expAmount);

		Jobs.FASTPAYMENT.put(jPlayer.getUniqueId(),
				new FastPayment(jPlayer, null, new BufferedPayment(jPlayer.getPlayer(), payments), prog.getJob()));

		Jobs.getEconomy().pay(jPlayer, payments);
		int oldLevel = prog.getLevel();

		if (Jobs.getGCManager().LoggingUse) {
			Map<CurrencyType, Double> amounts = new HashMap<>();
			amounts.put(CurrencyType.MONEY, income);
			amounts.put(CurrencyType.EXP, expAmount);
			amounts.put(CurrencyType.POINTS, pointAmount);
			Jobs.getLoging().recordToLog(jPlayer, null, amounts);
		}

		if (prog.addExperience(expAmount))
			Jobs.getPlayerManager().performLevelUp(jPlayer, prog.getJob(), oldLevel);
	}
}
