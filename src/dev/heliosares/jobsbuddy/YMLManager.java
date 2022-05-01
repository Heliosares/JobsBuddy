package dev.heliosares.jobsbuddy;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.Files;

import org.bukkit.Bukkit;
import java.io.File;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public class YMLManager {
	Plugin plugin;
	FileConfiguration data;
	private File dfile;
	private final String fileName;

	public YMLManager(String fileName, Plugin p) {
		this.fileName = fileName;
		this.plugin = p;
	}

	public void load() {
		if (!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdir();
		}
		this.dfile = new File(plugin.getDataFolder(), fileName + ".yml");
		if (!this.dfile.exists()) {
			try {
				this.dfile.createNewFile();
			} catch (IOException e) {
				Bukkit.getServer().getLogger().severe("§4Could not create " + fileName + ".yml!");
			}
		}
		this.data = (FileConfiguration) YamlConfiguration.loadConfiguration(this.dfile);
	}

	public FileConfiguration getData() {
		return this.data;
	}

	public void save() {
		try {
			this.data.save(this.dfile);
		} catch (IOException e) {
			Bukkit.getServer().getLogger().severe("§4Could not save " + fileName + ".yml!");
		}
	}

	public void reload() {
		this.data = (FileConfiguration) YamlConfiguration.loadConfiguration(this.dfile);
	}

	public File getFile() {
		return dfile;
	}

	public boolean backup() {
		File destination = null;
		int i = 0;
		do {
			destination = new File(plugin.getDataFolder(), "backups/" + fileName + (i > 0 ? "-" + i : "") + ".yml");
			destination.getParentFile().mkdirs();
			i++;
			if (i > 10000) {
				plugin.getLogger().warning("§cBackups folder full. Delete some backups to proceed.");
				return false;
			}
		} while (destination.exists());
		try {
			Files.copy(dfile.toPath(), destination.toPath());
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
}
