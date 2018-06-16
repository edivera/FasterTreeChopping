package me.bukkit.fastergameplay;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class FasterTreeChopping extends JavaPlugin {
	
	public void onEnable() {
		new WoodChoppedListener(this);
		getLogger().info("----------------FasterTreeChoppingEnabled----------------");
	}
	
	public void onDisable() {
		
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		
		switch(cmd.getName()) {
			case "pitchup": 
				WoodChoppedListener.pitch += 0.033; 
				break;
			case "pitchdown":
				WoodChoppedListener.pitch -= 0.033;
				break;
			default: return false;
		}
		
		if(sender instanceof Player) ((Player)sender).sendMessage("Pitch: " + WoodChoppedListener.pitch);
		return true;
	}
	
}
