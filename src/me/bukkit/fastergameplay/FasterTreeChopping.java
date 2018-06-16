package me.bukkit.fastergameplay;

import org.bukkit.plugin.java.JavaPlugin;

public class FasterTreeChopping extends JavaPlugin {
	
	public void onEnable() {
		new WoodChoppedListener(this);
		getLogger().info("----------------FasterTreeChoppingEnabled----------------");
	}
	
	public void onDisable() {
		
	}
	
}
