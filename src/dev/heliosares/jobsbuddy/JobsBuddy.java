package dev.heliosares.jobsbuddy;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import com.dre.brewery.recipe.BRecipe;
import com.dre.brewery.recipe.RecipeItem;
import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.api.JobsExpGainEvent;
import com.gamingmesh.jobs.api.JobsPrePaymentEvent;
import com.gamingmesh.jobs.container.ActionInfo;
import com.gamingmesh.jobs.container.Boost;
import com.gamingmesh.jobs.container.CurrencyLimit;
import com.gamingmesh.jobs.container.CurrencyType;
import com.gamingmesh.jobs.container.FastPayment;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;
import com.gamingmesh.jobs.economy.BufferedPayment;

public class JobsBuddy extends JavaPlugin {
	public static long limitDuration;
	public MyListener listener;
	public static final String permissionNodePrefix = "jobsbuddy.";
	protected YMLManager data;
	private HashMap<String, Parser> limitEquations;
	protected Parser brewIncomeModificationEquation;
	protected Parser brewExpModificationEquation;
	protected boolean perJobLimit;
	protected boolean customLimit;
	protected boolean breweryEarnings;
	public boolean debug;
	protected HashMap<Material, Double> itemValues;
	protected String brewerName;

	// TODO DYNAMIC LIMIT based on usage of a certain job compared to other jobs,
	// some range of percentage of overall job limit

	@Override
	public void onEnable() {
		this.getConfig().options().copyDefaults(true);
		this.saveDefaultConfig();
		loadConfig();

		Bukkit.getPluginManager().registerEvents(listener = new MyListener(this), this);

		if (this.getServer().getPluginManager().getPlugin("Brewery") != null) {
			Bukkit.getPluginManager().registerEvents(new BreweryListener(this), this);
			getLogger().info("Hooked into Brewery");
		}

		this.getCommand("jobsbuddy").setExecutor(new MyCommand(this));
		this.getCommand("jobsbuddy").setTabCompleter(new MyCommandTab(this));

		data = new YMLManager("data", this);
		data.load();

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

	public void loadConfig() {
		brewerName = getConfig().getString("brewer-name");
		limitDuration = getConfig().getLong("limit-duration");
		perJobLimit = getConfig().getBoolean("per-job-limit");
		customLimit = getConfig().getBoolean("custom-limit");
		breweryEarnings = getConfig().getBoolean("brewery-earnings");
		debug = getConfig().getBoolean("debug", false);
		brewIncomeModificationEquation = new Parser(getConfig().getString("brew-income-modification-equation"));
		brewExpModificationEquation = new Parser(getConfig().getString("brew-exp-modification-equation"));
		itemValues = new HashMap<>();
		limitEquations = new HashMap<>();

		ConfigurationSection limitequations = getConfig().getConfigurationSection("limit-equations");
		if (limitequations != null) {
			for (String key : limitequations.getKeys(false)) {
				limitEquations.put(key.toLowerCase(), new Parser(limitequations.getString(key)));
			}
		}

		/*
		 * ConfigurationSection breweryJobs =
		 * getConfig().getConfigurationSection("Brewery"); if (breweryJobs != null) {
		 * for (String job : breweryJobs.getKeys(false)) { ConfigurationSection brews =
		 * breweryJobs.getConfigurationSection(job); HashMap<String, Reward> rewards =
		 * new HashMap<>(); for (String brewName : brews.getKeys(false)) { double income
		 * = brews.getDouble(brewName + ".income"); double points =
		 * brews.getDouble(brewName + ".points"); double experience =
		 * brews.getDouble(brewName + ".experience");
		 * 
		 * Reward reward = new Reward(income, points, experience);
		 * rewards.put(ChatColor.stripColor(brewName).toLowerCase().replaceAll("[^\\w]",
		 * ""), reward); } this.breweryRewards.put(job, rewards); } }
		 */

		Set<Material> ingredients = new HashSet<>();
		for (BRecipe recipe : BRecipe.getConfigRecipes()) {
			for (RecipeItem item : recipe.getIngredients()) {
				if (item.hasMaterials()) {
					for (Material mat : item.getMaterials()) {
						ingredients.add(mat);
					}
				}
			}
		}

		Set<Material> unknown = new HashSet<>();
		for (Material mat : ingredients) {
			double value = getConfig().getDouble("ItemValues." + mat.toString());
			if (value < 0) {
				unknown.add(mat);
				value = 0.1;
			}
			itemValues.put(mat, value);
		}
		if (unknown.size() > 0) {
			getLogger().warning("Unknown value for:");
			for (Material mat : unknown) {
				getLogger().warning(mat.toString());
			}
		}

		/*
		 * new BukkitRunnable() {
		 * 
		 * @Override public void run() { Set<Material> unknown = new HashSet<>(); for
		 * (Material mat : ingredients) { double sell =
		 * ShopGuiPlusApi.getItemStackPriceSell(new ItemStack(mat, 1)); if (sell < 0) {
		 * Material other = null; double modifier = 1; if
		 * (mat.toString().endsWith("NUGGET")) { try { other =
		 * Material.valueOf(mat.toString().replace("NUGGET", "INGOT")); } catch
		 * (Exception ignored) { } modifier = 0.11111D; } else if
		 * (mat.toString().contains("BUCKET")) { other = Material.IRON_INGOT; modifier =
		 * 3; } else if (mat.toString().contains("LEAVES")) { other = Material.POTATO; }
		 * 
		 * if (other != null) { sell = ShopGuiPlusApi.getItemStackPriceSell(new
		 * ItemStack(other, 1)); if (sell > 0) { sell *= modifier; } } } if (sell < 0) {
		 * unknown.add(mat); sell = 0.1; } itemValues.put(mat, sell); }
		 * 
		 * if (unknown.size() > 0) { getLogger().warning("Unknown value for:"); for
		 * (Material mat : unknown) { getLogger().warning(mat.toString()); } } }
		 * }.runTaskLater(this, 20);
		 */

	}

	public double getValue(BRecipe recipe, int quality, CurrencyType type) {
		if (recipe == null) {
			return 0;
		}

		double value = 0;
		for (RecipeItem rItem : recipe.getIngredients()) {
			if (!rItem.hasMaterials() || rItem.getAmount() == 0) {
				continue;
			}
			for (Material mat : rItem.getMaterials()) {
				Double matValue = this.itemValues.get(mat);
				if (matValue != null && matValue > 0) {
					value += matValue * rItem.getAmount();
				}
			}
		}

		if (type == CurrencyType.MONEY) {
			brewIncomeModificationEquation.setVariable("value", value);
			brewIncomeModificationEquation.setVariable("difficulty", recipe.getDifficulty());
			brewIncomeModificationEquation.setVariable("quality", quality);

			return brewIncomeModificationEquation.solve();
		}
		brewExpModificationEquation.setVariable("value", value);
		brewExpModificationEquation.setVariable("difficulty", recipe.getDifficulty());
		brewExpModificationEquation.setVariable("quality", quality);

		return brewExpModificationEquation.solve();
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
			return parser.solve();
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public void giveReward(JobsPlayer jPlayer, JobProgression prog, double income, double pointAmount,
			double expAmount) {
		ActionInfo info = null;
		income = JobsBuddy.getIncome(prog.getJob(), prog.getLevel(), jPlayer.getJobProgression().size(), income);
		expAmount = JobsBuddy.getExperience(prog.getJob(), prog.getLevel(), jPlayer.getJobProgression().size(),
				expAmount);
		pointAmount = JobsBuddy.getPoints(prog.getJob(), prog.getLevel(), jPlayer.getJobProgression().size(),
				pointAmount);
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

		if (!Jobs.getGCManager().PaymentMethodsMoney) {
			income = 0;
		}

		if (!Jobs.getGCManager().PaymentMethodsPoints) {
			pointAmount = 0;
		}

		if (!Jobs.getGCManager().PaymentMethodsExp) {
			expAmount = 0;
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

		@SuppressWarnings("unchecked")
		LinkedHashMap<UUID, FastPayment> FASTPAYMENT = (LinkedHashMap<UUID, FastPayment>) JobsBuddy
				.reflectFieldStatic(Jobs.class, "FASTPAYMENT");

		FASTPAYMENT.put(jPlayer.getUniqueId(),
				new FastPayment(jPlayer, info, new BufferedPayment(jPlayer.getPlayer(), payments), prog.getJob()));

		Jobs.getEconomy().pay(jPlayer, payments);
		int oldLevel = prog.getLevel();

		if (Jobs.getGCManager().LoggingUse) {
			Map<CurrencyType, Double> amounts = new HashMap<>();
			amounts.put(CurrencyType.MONEY, income);
			amounts.put(CurrencyType.EXP, expAmount);
			amounts.put(CurrencyType.POINTS, pointAmount);
			Jobs.getLoging().recordToLog(jPlayer, info, amounts);
		}

		if (prog.addExperience(expAmount))
			Jobs.getPlayerManager().performLevelUp(jPlayer, prog.getJob(), oldLevel);
	}

	public static double getIncome(Job job, double level, int numjobs, double baseIncome) {
		net.Zrips.CMILib.Equations.Parser equation = (net.Zrips.CMILib.Equations.Parser) reflectField(job,
				"moneyEquation");
		equation.setVariable("joblevel", level);
		equation.setVariable("numjobs", numjobs);
		equation.setVariable("baseincome", baseIncome);
		return equation.getValue();
	}

	public static double getExperience(Job job, double level, int numjobs, double baseExperience) {
		net.Zrips.CMILib.Equations.Parser equation = (net.Zrips.CMILib.Equations.Parser) reflectField(job,
				"xpEquation");
		equation.setVariable("joblevel", level);
		equation.setVariable("numjobs", numjobs);
		equation.setVariable("baseexperience", baseExperience);
		return equation.getValue();
	}

	public static double getPoints(Job job, double level, int numjobs, double basePoints) {
		net.Zrips.CMILib.Equations.Parser equation = (net.Zrips.CMILib.Equations.Parser) reflectField(job,
				"pointsEquation");
		equation.setVariable("joblevel", level);
		equation.setVariable("numjobs", numjobs);
		equation.setVariable("basepoints", basePoints);
		return equation.getValue();
	}

	public void debug(String string) {
		if (debug) {
			getLogger().info("DEBUG: " + string);
		}
	}

	public static Object reflectField(Object o, String fieldName) {
		try {
			Field field = o.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(o);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static Object reflectFieldStatic(Class<?> c, String fieldName) {
		try {
			Field field = c.getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
}
