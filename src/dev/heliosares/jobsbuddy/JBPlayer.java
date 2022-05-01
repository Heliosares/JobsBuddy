package dev.heliosares.jobsbuddy;

import java.util.HashMap;

import org.bukkit.OfflinePlayer;

import com.gamingmesh.jobs.Jobs;
import com.gamingmesh.jobs.container.Job;
import com.gamingmesh.jobs.container.JobsPlayer;

public class JBPlayer {
	protected final HashMap<String, Long> cooldown = new HashMap<>();
	protected final HashMap<String, Double> earned = new HashMap<>();
	public final OfflinePlayer player;
	public final JobsPlayer jPlayer;
	private final JobsBuddy plugin;

	protected JBPlayer(JobsBuddy plugin, OfflinePlayer player) {
		this.plugin = plugin;
		this.player = player;
		this.jPlayer = Jobs.getPlayerManager().getJobsPlayer(player.getUniqueId());
	}

	public long getCooldown(String job) {
		if (cooldown.containsKey(job)) {
			return cooldown.get(job);
		}
		return 0;
	}

	protected void initJob(String job, double earned, long cooldown) {
		this.cooldown.put(job, cooldown);
		this.earned.put(job, earned);
	}

	public double getEarned(String job) {
		long cooldown = getCooldown(job);
		if (cooldown < System.currentTimeMillis()) {
			earned.remove(job);
			this.cooldown.remove(job);
			return 0;
		}
		double currentamount = 0;
		if (earned.containsKey(job)) {
			currentamount = earned.get(job);
		}
		return currentamount;
	}

	public void earn(String job, double amount) {
		double currentamount = getEarned(job) + amount;
		if (!cooldown.containsKey(job)) {
			cooldown.put(job, System.currentTimeMillis() + JobsBuddy.limitDuration);
		}
		earned.put(job, currentamount);
	}

	public double canEarn(Job job, double amount) {
		double currentamount = 0;
		if (earned.containsKey(job.getName())) {
			currentamount = earned.get(job.getName());
		}
		double limit = getLimit(job);
		if (currentamount >= limit) {
			return 0;
		} else if (amount + currentamount > limit) {
			return limit - currentamount;
		}
		return amount;
	}

	public double getLimit(Job job) {
		int level = jPlayer.getJobProgression(job).getLevel();

		return plugin.getLimit(job.getName(), level);
	}
}
