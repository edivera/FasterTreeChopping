package me.bukkit.fastergameplay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.TreeSpecies;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Leaves;
import org.bukkit.material.Wood;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.player.PlayerItemBreakEvent;

public class WoodChoppedListener implements Listener {

	private final static boolean fail = false;
	private final static boolean success = true;

	private Plugin parentPlugin; // TODO: get rid of

	private LinkedList<Block> bfsQueue;			// general use bfs queue
	private HashSet<Block> bfsDiscoveredSet;	// general use bfs discovered set
	
	private HashSet<Block> logsWithLeaves;		// log heads are log blocks with leaves next to them
	private HashSet<Block> nearByLogs;			// store the nearby logs to tree leaves to prevent double bfs

	private LinkedList<Integer> leafRadi; // radi of the leaves
	
	private LinkedList<Block> leavesToDecay; // stored leaves scheduled to decay
	private HashSet<Block> leavesNotToDecay; // leaves supported by other trees
	
	private int complexity;	//debugging complexity TODO: remove

	public WoodChoppedListener(FasterTreeChopping plugin) {
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
		parentPlugin = plugin;
	}

	@EventHandler
	public void onWoodChopped(BlockBreakEvent e) {
		if (isNotATree(e.getBlock())) {
			// if block isn't a tree, return
			return;
		}

		ItemStack itemInHand = e.getPlayer().getInventory().getItemInMainHand();
		if (wasNotChoppedByAnAxe(itemInHand)) {
			// if it was not chopped by an axe, return
			return;
		}

		complexity = 0;	//TODO: remove
		
		if (chopFullTree(e.getBlock(), itemInHand) == fail) {
			// if your axe broke midway
			e.getPlayer().sendMessage("Your axe broke");
		}

		e.getPlayer().sendMessage("You chopped a tree");
		
		parentPlugin.getLogger().info("Complexity: " + complexity);	//TODO: remove
	}

	private boolean isATree(Block block) {
		return !isNotATree(block);
	}

	private boolean isNotATree(Block block) {
		if (block.getType() != Material.LOG) {
			// not a wood log
			return true;
		}
		// it is a wood log, find the leaves by going up
		Block ptr = block;
		while (ptr.getType() == Material.LOG) {
			ptr = ptr.getRelative(0, 1, 0);
		}
		if (ptr.getType() != Material.LEAVES && ptr.getType() != Material.LEAVES_2) {
			// next is not leaves, this isn't a tree
			return true;
		}
		if (!((Leaves) ptr.getState().getData()).isDecayable()) {
			// these leaves were placed, fake tree
			return true;
		}
		return false;
	}

	private boolean wasNotChoppedByAnAxe(ItemStack itemInHand) {
		switch (itemInHand.getType()) {
		case DIAMOND_AXE:
		case GOLD_AXE:
		case IRON_AXE:
		case STONE_AXE:
		case WOOD_AXE:
			return false;
		default:
			return true;
		}
	}

	private boolean chopFullTree(Block base, ItemStack axeInHand) {
		if (chopLogsWithAxeBFS(base, axeInHand) == fail) {
			// axe broke
			return false;
		}
		decayTreeLeaves();
		return true;
	}

	private boolean chopLogsWithAxeBFS(Block base, ItemStack axeInHand) {
		bfsQueue = new LinkedList<Block>();			// clean bfs queue
		bfsDiscoveredSet = new HashSet<Block>();	// used to discover wood blocks for the tree
		logsWithLeaves = new HashSet<Block>();		// stores the roots for the leaves
		
		bfsQueue.add(base);
		bfsDiscoveredSet.add(base);
		
		// BFS for wood
		while (!bfsQueue.isEmpty()) {
			// get current head
			Block block = bfsQueue.removeFirst();
			// discovered undiscovered surrounding blocks
			discoverSurroundingWoodBlocks(block);
			// break block
			if (breakWoodBlock(block, axeInHand) == fail) {
				// if axe is broken
				return false;
			}
		}
		return true;
	}

	private void discoverSurroundingWoodBlocks(Block head) {
		for (int i = -1; i <= 1; i++) {
			for (int j = -1; j <= 1; j++) {
				for (int k = -1; k <= 1; k++) {
					if((i == k || i == -k) && (i != 0 && k != 0)) continue;
					boolean headHasLeaves = discoverSurroundingBlock(head.getRelative(i, j, k));
					if (headHasLeaves && !logsWithLeaves.contains(head)) {
						logsWithLeaves.add(head);
					}
				}
			}
		}
	}

	private boolean discoverSurroundingBlock(Block blockToDiscover) {
		parentPlugin.getLogger().info("" + blockToDiscover.getType() + " " + !bfsDiscoveredSet.contains(blockToDiscover));
		if ((blockToDiscover.getType() == Material.LOG) && !bfsDiscoveredSet.contains(blockToDiscover)) {
			bfsQueue.addLast(blockToDiscover);
			bfsDiscoveredSet.add(blockToDiscover);
		} else if (blockToDiscover.getType() == Material.LEAVES || blockToDiscover.getType() == Material.LEAVES_2) {
			return true;
		}
		return false;
	}

	private void decayTreeLeaves() {
		parentPlugin.getLogger().info("Decaying leaves");
		nearByLogs = new HashSet<Block>();			// keeps the nearby logs to prevent searching twice
		leavesToDecay = new LinkedList<Block>();	// leaves to decay
		leavesNotToDecay = new HashSet<Block>();	// protects the supported leaves from decaying
		
		// search for nearby logs within a chunk radius
		for (Block head : logsWithLeaves) {
			for (int xOff = -8; xOff < 8; xOff++) {
				for(int yOff = -8; yOff < 8; yOff++) {	//TODO: could be optimized
					for (int zOff = -8; zOff < 8; zOff++) {
						Block relative = head.getRelative(xOff, 0, zOff);
						if (relative.getType() == Material.LOG && isATree(relative)) {
							if(nearByLogs.contains(relative)) {
								continue;
							}
							nearByLogs.add(relative);
							protectLeavesAround(relative);
						}
					}
				}
			}
		}
		
		
		bfsQueue = new LinkedList<Block>();			// clean bfs queue
		
		for (Block head : logsWithLeaves) {
			bfsDiscoveredSet = new HashSet<Block>();	// used to discover leaves from the chopped tree
			bfsDiscoverAdjacentLeaves(head);
			
			while (!bfsQueue.isEmpty()) {
				Block leaf = bfsQueue.removeFirst();
				
				bfsDiscoverAdjacentLeaves(leaf);
				
				scheduleLeafToDecay(leaf);  //TODO: this is what neeeds to change to decide which decays
				complexity++;	//TODO: remove
			}
		}

		// decay all leaves at once at the end
		decayLeaves();

	}

	private void protectLeavesAround(Block wood) {
		
		bfsQueue = new LinkedList<Block>();			// clean bfs queue
		bfsDiscoveredSet = new HashSet<Block>();	// used to discover leaves that are supported by other trees
		
		leafRadi = new LinkedList<Integer>();
		
		bfsDiscoverAdjacentLeaves(wood);
		for(int i = 0; i < bfsQueue.size(); i++) leafRadi.add(1);
		
		while(!bfsQueue.isEmpty()) {
			Block head = bfsQueue.removeFirst();
			int currentRadius = leafRadi.removeFirst();
			if(currentRadius > 4) continue;
			
			if(!leavesNotToDecay.contains(head))
				leavesNotToDecay.add(head);
			
			int discovered = bfsDiscoverAdjacentLeaves(head);
			
			for(int i = 0; i < discovered; i++) leafRadi.add(currentRadius + 1);
			complexity++;	//TODO: remove
		}
		
	}

	private void decayLeaves() {
		for (Block decayingLeaf : leavesToDecay) {
			decayingLeaf.breakNaturally();
		}
	}

//	private void discoverLeavesWithinRadius(Block head, int maxRadius) {
//		// discover first leaves
//		bfsDiscoverAdjacentLeaves(head, Material.LEAVES, Material.LEAVES_2);
//		// each of those leaves has a radius of 1
//		for (Block leaf : bfsQueue)
//			leafRadi.add(1);
//
//		while (!bfsQueue.isEmpty()) {
//			head = bfsQueue.removeFirst();
//			int currentRadius = leafRadi.removeFirst();
//
//			if (maxRadius < currentRadius) {
//				continue;
//			}
//
//			int discovered = bfsDiscoverAdjacentLeaves(head, Material.LEAVES, Material.LEAVES_2);
//
//			// increment radius for next neighbor leaves
//			for (int i = 0; i < discovered; i++)
//				leafRadi.addLast(currentRadius + 1);
//
//			scheduleLeafToDecay(head);
//
//		}
//	}

	private int bfsDiscoverAdjacentLeaves(Block head) {
		final int[] xOff = { 0, -1, 1, 0,  0, 0};
		final int[] yOff = {-1,  0, 0, 0,  0, 1};
		final int[] zOff = { 0,  0, 0, 1, -1, 0};
		
		int discoveredCount = 0;
		for(int adjacent = 0; adjacent < 6; adjacent++) {
			discoveredCount += bfsDiscoverLeaf(head.getRelative(xOff[adjacent], yOff[adjacent], zOff[adjacent]));
		}
		
		return discoveredCount;
	}
	private int bfsDiscoverLeaf(Block leaf) {
		if (!bfsDiscoveredSet.contains(leaf) && isLeaf(leaf)) {
			bfsQueue.addLast(leaf);
			bfsDiscoveredSet.add(leaf);
			return 1;
		}
		return 0;
	}

	private boolean isLeaf(Block block) {
		return block.getType() == Material.LEAVES || block.getType() == Material.LEAVES_2;
	}

	private void discoverLeafBlock(Block blockToDiscover) {
		Material type = blockToDiscover.getType();
		if ((type == Material.LEAVES || type == Material.LEAVES_2) && !bfsDiscoveredSet.contains(blockToDiscover)
				&& ((Leaves) blockToDiscover.getState().getData()).isDecaying()) {
			bfsQueue.addLast(blockToDiscover);
			bfsDiscoveredSet.add(blockToDiscover);
		}
	}

	private boolean breakWoodBlock(Block blockToBreak, ItemStack playerAxe) {
		//parentPlugin.getLogger().info("Breaking wood block");
		blockToBreak.breakNaturally(playerAxe);
		// parentPlugin.getLogger().info("Durability: " + playerAxe.getDurability());
		playerAxe.setDurability((short) (playerAxe.getDurability() + 1));
		if (playerAxe.getDurability() < 0) {
			// TODO: get the axe to break
			// parentPlugin.getServer().getPluginManager().callEvent(
			// new PlayerItemBreakEvent(e.getPlayer(), playerAxe));
			return false;
		}
		return true;
	}

	private void scheduleLeafToDecay(Block leaf) {
		//parentPlugin.getLogger().info("Decay leaf block");
		
		if(!leavesNotToDecay.contains(leaf)) {
			leavesToDecay.add(leaf);
		}
	}
}
