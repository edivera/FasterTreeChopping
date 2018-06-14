package me.bukkit.fastergameplay;

import java.util.HashSet;
import java.util.LinkedList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
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
	private HashSet<Block> logHeads;			//log heads are log blocks with leaves next to them
	
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
		bfsQueue = new LinkedList<Block>();
		bfsQueue.add(base);
		bfsDiscoveredSet = new HashSet<Block>();
		bfsDiscoveredSet.add(base);
		logHeads = new HashSet<Block>();
		
		if(!chopLogsWithAxeBFS(axeInHand)) {
			//axe broke
			return false;
		}
		leafDecay();
		return true;
	}
	private boolean chopLogsWithAxeBFS(ItemStack axeInHand) {
		//BFS for wood
		while(!bfsQueue.isEmpty()) {
			//get current head
			Block block = bfsQueue.removeFirst();
			//discovered undiscovered surrounding blocks
			discoverSurroundingWoodBlocks(block);
			//break block
			if(breakWoodBlock(block, axeInHand) == fail) {
				//if axe is broken
				return false;
			}
		}
		return true;
	}
	private void leafDecay() {
		parentPlugin.getLogger().info("Decaying leaves");
		for(Block head : logHeads) {
			//DFS discover and decay surrounding leaves up to 5 blocks away
			discoverSupportedLeaves(head, 0);
		}
		
		
		//search for nearby connected logs (within a radius of 4)
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
					boolean headHasLeaves = discoverSurroundingBlock(head.getRelative(i, j, k));
					if(headHasLeaves && !logHeads.contains(head)) {
						logHeads.add(head);
					}
				}
			}
		}
	}
	private void discoverSupportedLeaves(Block node, int number) {
		for(int i = -1; i <= 1; i++) {
			for(int j = -1; j <= 1; j++) {
				for(int k = -1; k <= 1; k++) {
					decaySupportedLeavesDFS(node.getRelative(i, j, k), number + 1);
				}
			}
		}
	}
	private void decaySupportedLeavesDFS(Block node, int number) {
		if(number > 5 || isNotALeaf(node)) {
			return;
		}
		discoverSupportedLeaves(node, number);
		decayLeaf(node);
	}
	private boolean isNotALeaf(Block block) {
		return block.getType() != Material.LEAVES && block.getType() != Material.LEAVES_2;
	}
	private boolean discoverSurroundingBlock(Block blockToDiscover) {
		if((blockToDiscover.getType() == Material.LOG) 
				&& !bfsDiscoveredSet.contains(blockToDiscover)) {
			bfsQueue.addLast(blockToDiscover);
			bfsDiscoveredSet.add(blockToDiscover);
		}
		else if(blockToDiscover.getType() == Material.LEAVES || blockToDiscover.getType() == Material.LEAVES_2) {
			return true;
		}
		return false;
	}
	private void discoverLeafBlock(Block blockToDiscover) {
		Material type = blockToDiscover.getType();
		if((type == Material.LEAVES || type == Material.LEAVES_2) 
				&& !bfsDiscoveredSet.contains(blockToDiscover)
				&& ((Leaves)blockToDiscover.getState().getData()).isDecaying()) {
			bfsQueue.addLast(blockToDiscover);
			bfsDiscoveredSet.add(blockToDiscover);
		}
	}
	private boolean breakWoodBlock(Block blockToBreak, ItemStack playerAxe) {
		parentPlugin.getLogger().info("Breaking wood block");
		blockToBreak.breakNaturally(playerAxe);
		//parentPlugin.getLogger().info("Durability: " + playerAxe.getDurability());
		playerAxe.setDurability((short)(playerAxe.getDurability() + 1));
		if(playerAxe.getDurability() < 0) {
			//TODO: get the axe to break
//			parentPlugin.getServer().getPluginManager().callEvent(
//					new PlayerItemBreakEvent(e.getPlayer(), playerAxe));
			return false;
		}
		return true;
	}
	private void decayLeaf(Block leaf) {
		parentPlugin.getLogger().info("Decay leaf block");
		//((Leaves)leaf.getState().getData()).setDecaying(true);
		Bukkit.getServer().getPluginManager().callEvent(new LeavesDecayEvent(leaf));
	}
	private boolean breakLeafBlock(Block blockToBreak) {
		//TODO: change
		if(!logNearBy(blockToBreak)) {
			blockToBreak.breakNaturally();
		}
		return true;
	}
	
	private boolean logNearBy(Block head) {
		
		return false;
	}
}
