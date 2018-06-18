package me.bukkit.fastergameplay;

import java.util.HashSet;
import java.util.LinkedList;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Leaves;
import org.bukkit.plugin.Plugin;

public class WoodChoppedListener implements Listener {

	private final static boolean fail = false;
	private final static boolean success = true;
	
	private BlockBreakEvent thisEvent;	// reference to the current event being handled
	
	private LinkedList<Block> bfsQueue;			// general use bfs queue
	private HashSet<Block> bfsDiscoveredSet;	// general use bfs discovered set
	
	private HashSet<Block> logsWithLeaves;		// log heads are log blocks with leaves next to them
	private HashSet<Block> nearByLogs;			// store the nearby logs to tree leaves to prevent double bfs

	private LinkedList<Integer> leafRadi; // radi of the leaves
	
	private LinkedList<Block> leavesToDecay; // stored leaves scheduled to decay
	private HashSet<Block> leavesNotToDecay; // leaves supported by other trees
	
	public WoodChoppedListener(FasterTreeChopping plugin) {
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	
	@EventHandler
	public void onWoodChopped(BlockBreakEvent blockBreakEvent) {
		thisEvent = blockBreakEvent;
		
		if (isNotATree(blockBreakEvent.getBlock())) {
			// if block isn't a tree, return
			return;
		}

		ItemStack axeInHand = blockBreakEvent.getPlayer().getInventory().getItemInMainHand();
		if (wasNotChoppedWithAnAxe(axeInHand)) {
			// if it was not chopped using an axe, return
			return;
		}

		if (chopFullTreeWithAxe(axeInHand) == fail) {
			// if your axe broke midway
			blockBreakEvent.getPlayer().sendMessage("Your axe broke");
			return;
		}
		blockBreakEvent.getPlayer().sendMessage("You chopped a tree");
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
	private boolean wasNotChoppedWithAnAxe(ItemStack itemInHand) {
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

	private boolean chopFullTreeWithAxe(ItemStack axeInHand) {
		Block base = thisEvent.getBlock();
		if (chopLogsWithAxeBFS(base, axeInHand) == fail) {
			// axe broke
			return fail;
		}
		decayTreeLeaves();
		return success;
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
				return fail;
			}
		}
		return success;
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
		if ((blockToDiscover.getType() == Material.LOG) && !bfsDiscoveredSet.contains(blockToDiscover)) {
			bfsQueue.addLast(blockToDiscover);
			bfsDiscoveredSet.add(blockToDiscover);
		} else if (blockToDiscover.getType() == Material.LEAVES || blockToDiscover.getType() == Material.LEAVES_2) {
			return true;
		}
		return false;
	}
	private boolean breakWoodBlock(Block blockToBreak, ItemStack playerAxe) {
		blockToBreak.breakNaturally(playerAxe);
		
		consumeAxeUse(playerAxe);
		
		if (playerAxe.getType().getMaxDurability() - playerAxe.getDurability() < 0) {
			thisEvent.getPlayer().playSound(thisEvent.getPlayer().getLocation(), Sound.ENTITY_ITEM_BREAK,
					3.0f, 0.866f);
			thisEvent.getPlayer().getInventory().remove(playerAxe);
			return fail;
		}
		return success;
	}
	
	private void consumeAxeUse(ItemStack playerAxe) {
		int unbreaking = playerAxe.getEnchantmentLevel(Enchantment.DURABILITY);
		if(Math.random() <= 1.0 / (unbreaking + 1))
			playerAxe.setDurability((short)(playerAxe.getDurability() + 1));
	}


	private void decayTreeLeaves() {
		// search for nearby logs within a chunk radius and protects leaves
		protectLeavesAroundCloseLogs();
		
		findUnprotectedLeavesToDecayBFS();

		// decay all leaves at once at the end
		decayLeaves();
	}
	private void protectLeavesAroundCloseLogs() {
		nearByLogs = new HashSet<Block>();			// keeps the nearby logs to prevent searching twice
		leavesNotToDecay = new HashSet<Block>();	// protects the supported leaves from decaying
		// finds close logs
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
							protectLeavesAroundBFS(relative);
						}
					}
				}
			}
		}
	}
	private void protectLeavesAroundBFS(Block wood) {
		
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
			
			for(int i = 0; i < discovered; i++) leafRadi.addLast(currentRadius + 1);
		}
		
	}
	private void findUnprotectedLeavesToDecayBFS() {
		leavesToDecay = new LinkedList<Block>();	// leaves to decay
		bfsQueue = new LinkedList<Block>();			// clean bfs queue
		
		for (Block head : logsWithLeaves) {				//TODO: could be optimized with Djiktra's
			leafRadi = new LinkedList<Integer>();		// used to constrain bfs
			bfsDiscoveredSet = new HashSet<Block>();	// used to discover leaves from the chopped tree
			bfsDiscoverAdjacentLeaves(head);
			
			for(int i = 0; i < bfsQueue.size(); i++) leafRadi.addLast(1);
			
			while (!bfsQueue.isEmpty()) {
				Block leaf = bfsQueue.removeFirst();
				int radius = leafRadi.removeFirst();
				if(radius > 5) continue;
				
				int discovered = bfsDiscoverAdjacentLeaves(leaf);
				
				for(int i = 0; i < discovered; i++) leafRadi.addLast(radius + 1);
				
				scheduleLeafToDecay(leaf);
			}
		}
	}
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
		if (!bfsDiscoveredSet.contains(leaf) && isALeaf(leaf)) {
			bfsQueue.addLast(leaf);
			bfsDiscoveredSet.add(leaf);
			return 1;
		}
		return 0;
	}
	private boolean isALeaf(Block block) {
		return block.getType() == Material.LEAVES || block.getType() == Material.LEAVES_2;
	}
	private void scheduleLeafToDecay(Block leaf) {
		if(!leavesNotToDecay.contains(leaf) && !leavesToDecay.contains(leaf)) {
			leavesToDecay.add(leaf);
		}
	}
	private void decayLeaves() {
		for (Block decayingLeaf : leavesToDecay) {
			decayingLeaf.breakNaturally();
		}
	}
}
