package me.bukkit.fastergameplay;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.plugin.Plugin;

public class LeafDecayTest implements Listener {
	
	private Plugin parentPlugin;
	
	public LeafDecayTest(FasterTreeChopping plugin) {
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		parentPlugin = plugin;
	}
	
	@EventHandler
	public void onLeafDecay(LeavesDecayEvent e) {
		parentPlugin.getLogger().info("Ran through here");
	}
}
