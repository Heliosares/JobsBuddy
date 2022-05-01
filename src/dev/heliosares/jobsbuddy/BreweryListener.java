package dev.heliosares.jobsbuddy;

import java.lang.reflect.Field;
import java.util.HashMap;

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

public class BreweryListener implements Listener {

	private final JobsBuddy plugin;
	private final HashMap<String, HashMap<String, Reward>> breweryRewards;

	public BreweryListener(JobsBuddy plugin, HashMap<String, HashMap<String, Reward>> breweryRewards) {
		this.plugin = plugin;
		this.breweryRewards = breweryRewards;
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
				plugin.getLogger().warning("full (" + lastLevel + "/" + fill + ")");
				return;
			}
			BIngredients ingredients = (BIngredients) reflectField(lastCauldron, "ingredients");
			if (ingredients == null) {
				return;
			}
			ItemStack item = ingredients.cook(lastCauldron.getState());
			Brew brew = BreweryApi.getBrew(item);

			String recipename = brew.getCurrentRecipe().getRecipeName();
			plugin.getLogger()
					.warning(event.getPlayer().getName() + " brewed " + recipename + " quality " + brew.getQuality());
		}
		lastCauldron = null;
		lastLevel = -1;
		/*
		 * event.getBrew().get JobsPlayer jPlayer =
		 * Jobs.getPlayerManager().getJobsPlayer(event.) for(JobProgression prog : )
		 */
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

	@EventHandler
	public void onBrewModifyEvent(BrewModifyEvent event) {
		if (event.getType() == Type.SEAL) {
			for (Player player : Bukkit.getOnlinePlayers()) {
				InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
				if (holder == null) {
					continue;
				}
				if (!(holder instanceof BSealer)) {
					continue;
				}
				BSealer bsealer = (BSealer) holder;
				short[] slotTime = (short[]) reflectField(bsealer, "slotTime");
				if (slotTime == null) {
					return;
				}
				for (int i = 0; i < bsealer.getInventory().getSize() && i < slotTime.length; i++) {
					ItemStack item = bsealer.getInventory().getItem(i);
					if (item == null) {
						continue;
					}
					short time = slotTime[i];
					if (time != -1) {
						continue;
					}
					Brew brew = BreweryApi.getBrew(item);

					if (event.getBrew().getCurrentRecipe().equals(brew.getCurrentRecipe())) {
						plugin.getLogger()
								.warning(player.getName() + " sealed " + event.getBrew().getCurrentRecipe().getName(5)
										+ " quality " + event.getBrew().getQuality());
					}
				}
				// slotTime is -1 when sealing, look for that, should be the only one maybe
				// Can multiple players view a sealing table? No.
				// https://github.com/DieReicheErethons/Brewery/blob/556ce7ed348e21425628e7d8cd09aeed55f927f6/src/com/dre/brewery/BSealer.java#L95
			}
		}
	}
}
