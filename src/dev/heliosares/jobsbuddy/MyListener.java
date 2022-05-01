package dev.heliosares.jobsbuddy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.api.JobsPrePaymentEvent;
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
		if (event.getMessage().toLowerCase().startsWith("/jobs limit")) {
			String args[] = event.getMessage().split(" ");
			event.setCancelled(true);
			if (!MyPermission.LIMIT.hasPermission(event.getPlayer())) {
				event.getPlayer().sendMessage("§cYou do not have permission for this command.");
				return;
			}

			OfflinePlayer target = event.getPlayer();

			if (args.length > 2) {
				if (!MyPermission.LIMIT_OTHERS.hasPermission(event.getPlayer())) {
					event.getPlayer().sendMessage("§cYou do not have permission to check other player's limits.");
					return;
				}
				target = Bukkit.getOfflinePlayer(args[2]);
			}
			JobsPlayer jobsPlayer = null;
			if (target == null || (jobsPlayer = Jobs.getPlayerManager().getJobsPlayer(target.getUniqueId())) == null) {
				event.getPlayer().sendMessage("§cPlayer not found.");
				return;
			}
			JBPlayer jbPlayer = plugin.getPlayer(target);
			event.getPlayer().sendMessage("§6§lJobs limits:");
			if (jobsPlayer.getJobProgression().size() > 0) {
				for (JobProgression job : jobsPlayer.getJobProgression()) {
					String jobName = job.getJob().getName();
					double earned = jbPlayer.getEarned(jobName);
					long cooldown = jbPlayer.getCooldown(jobName);
					double limit = jbPlayer.getLimit(job.getJob());

					String message = String.format("%s§e limit: %s/%s", job.getJob().getJobDisplayName(),
							JobsBuddy.formatMoney(earned), JobsBuddy.formatMoney(limit));
					if (cooldown > System.currentTimeMillis()) {
						message += ", resets in: " + JobsBuddy.formatTime(cooldown - System.currentTimeMillis(), true);
						// TODO convert time
					}
					event.getPlayer().sendMessage(message);
				}
				event.getPlayer().sendMessage("");
			}
			double amount = jobsPlayer.getPaymentLimit().getAmount(CurrencyType.MONEY);
			int limit = jobsPlayer.getLimit(CurrencyType.MONEY);
			String message = String.format("§eOverall limit: %s/%s", JobsBuddy.formatMoney(amount),
					JobsBuddy.formatMoney(limit));
			long cooldown = jobsPlayer.getPaymentLimit().getLeftTime(CurrencyType.MONEY);
			if (cooldown > 0) {
				message += ", resets in: " + JobsBuddy.formatTime(cooldown, true);
				// TODO convert time
			}
			event.getPlayer().sendMessage(message);
		}
	}

	@EventHandler
	public void onPrePay(JobsPrePaymentEvent event) {
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
