package dev.heliosares.jobsbuddy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.dre.brewery.BCauldron;
import com.dre.brewery.BIngredients;
import com.dre.brewery.BSealer;
import com.dre.brewery.Brew;
import com.dre.brewery.api.BreweryApi;
import com.dre.brewery.api.events.brew.BrewModifyEvent;
import com.dre.brewery.api.events.brew.BrewModifyEvent.Type;
import com.dre.brewery.utility.LegacyUtil;
import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.CurrencyType;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;

import net.md_5.bungee.api.ChatColor;

public class BreweryListener implements Listener {

	private final JobsBuddy plugin;

	public BreweryListener(JobsBuddy plugin) {
		this.plugin = plugin;
	}

	private BCauldron lastCauldron;
	private int lastLevel;

	@EventHandler(priority = EventPriority.LOWEST)
	public void onBrew(PlayerInteractEvent event) {
		lastCauldron = null;
		Block clickedBlock = event.getClickedBlock();
		if (clickedBlock == null) {
			return;
		}
		if (LegacyUtil.isWaterCauldron(event.getClickedBlock().getType())) {
			Material materialInHand = event.getMaterial();
			ItemStack item = event.getItem();

			if (materialInHand == Material.GLASS_BOTTLE) {
				assert item != null;
				if (event.getPlayer().getInventory().firstEmpty() != -1 || item.getAmount() == 1) {
					lastCauldron = BCauldron.get(clickedBlock);
					lastLevel = getLevel(clickedBlock.getBlockData());
				}
			}
		}
	}

	public int getLevel(BlockData data) {
		if (data == null) {
			return -1;
		}
		if (!(data instanceof Levelled)) {
			return -1;
		}
		return ((Levelled) data).getLevel();
	}

	/**
	 * Much better wayu is to listen BrewModifyEvent, its called when filing.
	 * Type.FILL
	 * 
	 * @param event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onBrewMonitor(PlayerInteractEvent event) {

		Block clickedBlock = event.getClickedBlock();
		if (clickedBlock == null || lastCauldron == null) {
			return;
		}
		if (clickedBlock.equals(lastCauldron.getBlock())) {
			int fill = getLevel(clickedBlock.getBlockData());
			if (lastLevel <= fill) {
				return;
			}
			BIngredients ingredients = (BIngredients) JobsBuddy.reflectField(lastCauldron, "ingredients");
			if (ingredients == null) {
				return;
			}
			ItemStack item = ingredients.cook(lastCauldron.getState());
			Brew brew = BreweryApi.getBrew(item);

			String recipename = brew.getCurrentRecipe().getRecipeName();
			plugin.debug(event.getPlayer().getName() + " brewed " + recipename + " quality " + brew.getQuality());
		}
		lastCauldron = null;
		lastLevel = -1;
		/*
		 * event.getBrew().get JobsPlayer jPlayer =
		 * Jobs.getPlayerManager().getJobsPlayer(event.) for(JobProgression prog : )
		 */
	}

	private boolean compareBrews(Brew brew1, Brew brew2) {
		if (brew1.isSealed() != brew2.isSealed()) {
			if (Math.abs(brew1.getQuality() - brew2.getQuality()) > 1) {
				plugin.debug("q" + brew1.getQuality() + "!~" + brew2.getQuality());
				return false;
			}
			if (Math.abs(brew1.getOrCalcAlc() - brew2.getOrCalcAlc()) > 2) {
				plugin.debug("a" + brew1.getOrCalcAlc() + "!~" + brew2.getOrCalcAlc());
				return false;
			}
		} else {
			if (brew1.getQuality() != brew2.getQuality()) {
				plugin.debug("q" + brew1.getQuality() + "!=" + brew2.getQuality());
				return false;
			}
			if (brew1.getOrCalcAlc() != brew2.getOrCalcAlc()) {
				plugin.debug("a" + brew1.getOrCalcAlc() + "!=" + brew2.getOrCalcAlc());
				return false;
			}
		}
		if (!brew1.getCurrentRecipe().equals(brew2.getCurrentRecipe())) {
			plugin.debug("recipe");
			return false;
		}
		return true;
	}

	@EventHandler
	public void onBrewModifyEvent(BrewModifyEvent event) {
		if (event.getType() == Type.SEAL) {
			boolean found = false;
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (player.getOpenInventory() == null) {
					continue;
				}

				InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
				if (holder == null) {
					continue;
				}
				if (!(holder instanceof BSealer)) {
					continue;
				}
				BSealer bsealer = (BSealer) holder;
				short[] slotTime = (short[]) JobsBuddy.reflectField(bsealer, "slotTime");
				if (slotTime == null) {
					continue;
				}
				for (int i = 0; i < bsealer.getInventory().getSize() && i < slotTime.length; i++) {
					ItemStack item = bsealer.getInventory().getItem(i);
					if (item == null) {
						continue;
					}
					short time = slotTime[i];
					if (time == -1) {
						Brew original = BreweryApi.getBrew(item);
						if (!original.isSealed() && compareBrews(original, event.getBrew())) {
							if (found) {
								plugin.getLogger().warning("Attempted to reward duplicate brew!");
								continue;
							}
							found = true;
							String brewName = event.getBrew().getCurrentRecipe().getName(10);
							int quality = original.getQuality();
							int alc = original.getOrCalcAlc();
							brewName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', brewName))
									.replaceAll("[^\\w]", "").toLowerCase();
							plugin.debug(String.format("%s sealed %s, Quality: %d, Alc: %d", player.getName(), brewName,
									quality, alc));

							if (!plugin.breweryEarnings || !MyPermission.REWARD_BREWERY.hasPermission(player)) {
								continue;
							}

							JobsPlayer jPlayer = Jobs.getPlayerManager().getJobsPlayer(player);
							for (JobProgression job : jPlayer.getJobProgression()) {
								if (!job.getJob().getName().equals(plugin.brewerName)) {
									continue;
								}
								double baseMoney = plugin.getValue(original.getCurrentRecipe(), original.getQuality(),
										CurrencyType.MONEY);
								double baseExp = plugin.getValue(original.getCurrentRecipe(), original.getQuality(),
										CurrencyType.EXP);
								if (baseMoney >= 0 && baseExp >= 0) {
									double income = baseMoney;
									double exp = baseExp;
									double points = baseExp;

									plugin.debug(String.format(
											"Issuing reward to %s: BaseIncome: %s, BaseExp: %f, BasePoints: %f",
											player.getName(), JobsBuddy.formatMoney(income), exp, points));

									plugin.giveReward(jPlayer, job, income, exp, points);
								}
							}
						}
					}
				}
				// slotTime is -1 when sealing, look for that, should be the only one maybe
				// Can multiple players view a sealing table? No.
				// https://github.com/DieReicheErethons/Brewery/blob/556ce7ed348e21425628e7d8cd09aeed55f927f6/src/com/dre/brewery/BSealer.java#L95
			}
			if (!found) {
				plugin.getLogger().warning("Unable to find brew to reward!");
			}
		}
	}
}
