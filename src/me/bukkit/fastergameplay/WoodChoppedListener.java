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

	private Plugin parentPlugin;
	private LinkedList<Block> bfsQueue;
	private HashSet<Block> discoveredBlocks;
	
	public WoodChoppedListener(FasterTreeChopping plugin) {
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		parentPlugin = plugin;
	}
	
	@EventHandler
	public void onWoodChopped(BlockBreakEvent e) {
		//If a player did not break the block, return
		if(e.getPlayer() == null) {
			return;
		}
		
		//Is it a tree?
		if(!isBaseOfTree(e.getBlock()) || !wasChoppedByAxe(e)) {
			//if block isn't the base of a tree, return
			//Bukkit.getConsoleSender().sendMessage("Not a tree");
			return;
		}
		e.getPlayer().sendMessage("You chopped a tree");
		
		//BFS to chop the full tree
		chopFullTreeBFS(e);
	}
	
	private boolean isBaseOfTree(Block block) {
		if(block.getType() != Material.LOG) {
			//not a wood log
			//Bukkit.getConsoleSender().sendMessage("Not wood");
			return false;
		}
		//it is a wood log, find the leaves by going up
		Block ptr = block;
		while(ptr.getType() == Material.LOG) {
			ptr = ptr.getRelative(0, 1, 0);
		}
		if(ptr.getType() != Material.LEAVES && ptr.getType() != Material.LEAVES_2) {
			//next is not leaves, this isn't a tree
			//Bukkit.getConsoleSender().sendMessage("Top of wood not leaves");
			return false;
		}
		if(!((Leaves)ptr.getState().getData()).isDecayable()) {
			//these leaves were placed, fake tree
			//Bukkit.getConsoleSender().sendMessage("Fake leaves");
			return false;
		}
		return true;
	}
	private boolean wasChoppedByAxe(BlockBreakEvent e) {
		switch(e.getPlayer().getInventory().getItemInMainHand().getType()) {
			case DIAMOND_AXE:
			case GOLD_AXE:
			case IRON_AXE:
			case STONE_AXE:
			case WOOD_AXE: return true;
			default: return false;
		}
	}
	private void chopFullTreeBFS(BlockBreakEvent e) {
		Block base = e.getBlock();
		bfsQueue = new LinkedList<>();
		bfsQueue.add(base);
		discoveredBlocks = new HashSet<>();
		discoveredBlocks.add(base);
		
		Block top = base;
		//BFS for wood
		while(bfsQueue.size() > 0) {
			//get current head
			Block block = bfsQueue.removeFirst();
			//compare to the current top of the tree
			if(block.getY() > top.getY()) {
				top = block;
			}
			//discovered undiscovered surrounding blocks
			discoverSurroundingWoodBlocks(block);
			//break block
			if(!breakBlock(block, e)) {
				//if axe is broken
				return;
			}
		}
		bfsQueue.addFirst(top);
		//BFS for leaves
		while(bfsQueue.size() > 0) {
			//get current head
			Block block = bfsQueue.removeFirst();
			//discovered undiscovered surrounding blocks
			discoverSurroundingLeafBlocks(block);
			//break block
			if(block != top) {
				breakBlock(block, e);
			}
		}
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
			blockToBreak.breakNaturally();
		}
		return true;
	}
}
