package me.bukkit.fastergameplay;

import java.util.HashSet;
import java.util.LinkedList;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Leaves;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.player.PlayerItemBreakEvent;

public class WoodChoppedListener implements Listener {

	private final static boolean fail = false;
	private final static boolean success = true;
	
	private Plugin parentPlugin;	//TODO: get rid of
	
	private LinkedList<Block> bfsQueue;			//general bfs queue
	private HashSet<Block> bfsDiscoveredSet;	//general bfs discovered set
	private LinkedList<Block> logHeadBlocks;	//log heads are log blocks with leaves next to them
	
	public WoodChoppedListener(FasterTreeChopping plugin) {
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		parentPlugin = plugin;
	}
	
	@EventHandler
	public void onWoodChopped(BlockBreakEvent e) {
		if(isNotATree(e.getBlock())) {
			//if block isn't a tree, return
			return;
		}
		
		ItemStack itemInHand = e.getPlayer().getInventory().getItemInMainHand();
		if(wasNotChoppedByAnAxe(itemInHand)) {
			//if it was not chopped by an axe, return
			return;
		}
		
		if(chopFullTree(e.getBlock(), itemInHand) == fail) {
			//if your axe broke midway
			e.getPlayer().sendMessage("Your axe broke");
		}
		
		e.getPlayer().sendMessage("You chopped a tree");
	}
	
	private boolean isNotATree(Block block) {
		if(block.getType() != Material.LOG) {
			//not a wood log
			return true;
		}
		//it is a wood log, find the leaves by going up
		Block ptr = block;
		while(ptr.getType() == Material.LOG) {
			ptr = ptr.getRelative(0, 1, 0);
		}
		if(ptr.getType() != Material.LEAVES && ptr.getType() != Material.LEAVES_2) {
			//next is not leaves, this isn't a tree
			return true;
		}
		if(!((Leaves)ptr.getState().getData()).isDecayable()) {
			//these leaves were placed, fake tree
			return true;
		}
		return false;
	}
	private boolean wasNotChoppedByAnAxe(ItemStack itemInHand) {
		switch(itemInHand.getType()) {
			case DIAMOND_AXE:
			case GOLD_AXE:
			case IRON_AXE:
			case STONE_AXE:
			case WOOD_AXE: return false;
			default: return true;
		}
	}
	private boolean chopFullTree(Block base, ItemStack axeInHand) {
		bfsQueue = new LinkedList<>();
		bfsQueue.add(base);
		discoveredBlocks = new HashSet<>();
		discoveredBlocks.add(base);
		
		if(!chopLogsWithAxeBFS(axeInHand)) {
			//axe broke
			return false;
		}
		leafDecay();
		return true;
	}
	private boolean chopLogsWithAxeBFS(ItemStack axeInHand) {
		//BFS for wood
		while(bfsQueue.size() > 0) {
			//get current head
			Block block = bfsQueue.removeFirst();
			//discovered undiscovered surrounding blocks
			discoverSurroundingWoodBlocks(block);
			//break block
			if(breakBlock(block, axeInHand) == fail) {
				//if axe is broken
				return false;
			}
		}
		return true;
	}
	private void leafDecay() {
		//search for nearby connected logs (within a radius of 5)
			//connected implies that there is a path from the leaves to the log
		//add the nearby logs to a list
		
		//bfs the nearby leaves to the original log
		//if there are no logs nearby
			//break them
		//else calculate if the leaf is far away from the log (far away implies connected and further than 5 blocks)
			//if far away, break
			//else don't break
	}
	private void discoverSurroundingWoodBlocks(Block head) {
		for(int i = -1; i <= 1; i++) {
			for(int j = -1; j <= 1; j++) {
				for(int k = -1; k <= 1; k++) {
					discoverWoodBlock(head.getRelative(i, j, k));
				}
			}
		}
	}
	private void discoverSurroundingLeafBlocks(Block head) {
		for(int i = -1; i <= 1; i++) {
			for(int j = -1; j <= 1; j++) {
				for(int k = -1; k <= 1; k++) {
					discoverLeafBlock(head.getRelative(i, j, k));
				}
			}
		}
	}
	private void discoverWoodBlock(Block blockToDiscover) {
		if((blockToDiscover.getType() == Material.LOG) 
				&& !discoveredBlocks.contains(blockToDiscover)) {
			bfsQueue.addLast(blockToDiscover);
			discoveredBlocks.add(blockToDiscover);
		}
	}
	private void discoverLeafBlock(Block blockToDiscover) {
		Material type = blockToDiscover.getType();
		if((type == Material.LEAVES || type == Material.LEAVES_2) 
				&& !discoveredBlocks.contains(blockToDiscover)
				&& ((Leaves)blockToDiscover.getState().getData()).isDecaying()) {
			bfsQueue.addLast(blockToDiscover);
			discoveredBlocks.add(blockToDiscover);
		}
	}
	private boolean breakBlock(Block blockToBreak, BlockBreakEvent e) {
		ItemStack playerAxe = e.getPlayer().getInventory().getItemInMainHand();
		if(blockToBreak.getType() == Material.LOG) {
			blockToBreak.breakNaturally(playerAxe);
			//parentPlugin.getLogger().info("Durability: " + playerAxe.getDurability());
			playerAxe.setDurability((short)(playerAxe.getDurability() + 1));
			if(playerAxe.getDurability() < 0) {
				//TODO: get the axe to break
				parentPlugin.getServer().getPluginManager().callEvent(
						new PlayerItemBreakEvent(e.getPlayer(), playerAxe));
				return false;
			}
		}
		else {
			if(!logNearBy(blockToBreak)) {
				blockToBreak.breakNaturally();
			}
		}
		return true;
	}
	private boolean logNearBy(Block head) {
		
		return false;
	}
}
