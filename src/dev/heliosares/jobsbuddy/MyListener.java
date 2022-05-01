package dev.heliosares.jobsbuddy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.api.JobsPrePaymentEvent;
import com.gamingmesh.jobs.container.ActionInfo;
import com.gamingmesh.jobs.container.CurrencyType;
import com.gamingmesh.jobs.container.JobProgression;
import com.gamingmesh.jobs.container.JobsPlayer;

public class MyListener implements Listener {

	private final JobsBuddy plugin;

	public MyListener(JobsBuddy plugin) {
		this.plugin = plugin;
	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onCommandPre(PlayerCommandPreprocessEvent event) {
		if (!plugin.perJobLimit) {
			return;
		}
		String command = event.getMessage();
		if (command.startsWith("/")) {
			command = command.substring(1);
		}
		if (command.startsWith("jobs limit")) {
			String args[] = event.getMessage().split(" ");
			event.setCancelled(true);
			if (!MyPermission.LIMIT.hasPermission(event.getPlayer())) {
				event.getPlayer().sendMessage("�cYou do not have permission for this command.");
				return;
			}

			OfflinePlayer target = event.getPlayer();

			if (args.length > 2) {
				if (!MyPermission.LIMIT_OTHERS.hasPermission(event.getPlayer())) {
					event.getPlayer().sendMessage("�cYou do not have permission to check other player's limits.");
					return;
				}
				target = Bukkit.getOfflinePlayer(args[2]);
			}
			JobsPlayer jobsPlayer = null;
			if (target == null || (jobsPlayer = Jobs.getPlayerManager().getJobsPlayer(target.getUniqueId())) == null) {
				event.getPlayer().sendMessage("�cPlayer not found.");
				return;
			}
			JBPlayer jbPlayer = plugin.getPlayer(target);
			event.getPlayer().sendMessage("�e�lJobs limits:");
			if (jobsPlayer.getJobProgression().size() > 0) {
				for (JobProgression job : jobsPlayer.getJobProgression()) {
					String jobName = job.getJob().getName();
					double earned = jbPlayer.getEarned(jobName);
					long cooldown = jbPlayer.getCooldown(jobName);
					double limit = jbPlayer.getLimit(job.getJob());
					String color = "";
					double usage = earned / limit;
					if (usage > 0.99) {
						color = "�c";
					}

					String message = String.format("%s�e: %s/%s", job.getJob().getJobDisplayName(),
							color + JobsBuddy.formatMoney(earned), JobsBuddy.formatMoney(limit));
					if (cooldown > System.currentTimeMillis()) {
						message += "�e, resets in: "
								+ JobsBuddy.formatTime(cooldown - System.currentTimeMillis(), true);
						// TODO convert time
					}
					event.getPlayer().sendMessage(message);
				}
				String line = "�8�m";
				for (int i = 0; i < 20; i++) {
					line += (char) 65293;
				}
				event.getPlayer().sendMessage(line);
			}
			double amount = jobsPlayer.getPaymentLimit().getAmount(CurrencyType.MONEY);
			int limit = jobsPlayer.getLimit(CurrencyType.MONEY);
			String color = "";
			double usage = amount / (double) limit;
			if (usage > 0.99) {
				color = "�c";
			}
			String message = String.format("�eOverall: %s/%s", color + JobsBuddy.formatMoney(amount),
					JobsBuddy.formatMoney(limit));
			long cooldown = jobsPlayer.getPaymentLimit().getLeftTime(CurrencyType.MONEY);
			if (amount > 0.01) {
				message += "�e, resets in: " + JobsBuddy.formatTime(cooldown, true);
				// TODO convert time
			}
			event.getPlayer().sendMessage(message);
		}
	}
	
	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		Jobs.getPlayerManager().getJobsPlayer(e.getPlayer()).getJobProgression(Jobs.getJob("Brewer"));
		//new BrewActionInfo("");
	}

	@EventHandler
	public void onPrePay(JobsPrePaymentEvent event) {
		if (!plugin.perJobLimit) {
			return;
		}
		if (event.isCancelled()) {
			return;
		}
		if (event.getAmount() < 0) {
			return;
		}
		JBPlayer jPlayer = plugin.getPlayer(event.getPlayer());
		double canearn = jPlayer.canEarn(event.getJob(), event.getAmount());
		if (canearn < event.getAmount()) {
			event.setPoints(0);
			event.setAmount(canearn);
		}
		jPlayer.earn(event.getJob().getName(), canearn);

		plugin.getLogger().info(
				event.getPlayer().getName() + " earned $" + event.getAmount() + " from " + event.getJob().getName());
	}
}
