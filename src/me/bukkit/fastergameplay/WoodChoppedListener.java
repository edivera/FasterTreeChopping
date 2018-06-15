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

	private LinkedList<Block> bfsQueue; // general bfs queue
	private LinkedList<Integer> leafRadi; // radi of the leaves
//	private LinkedList<Block> nearByTrees; // surrounding tree wood blocks
	private LinkedList<Block> leavesToDecay; // stored leaves scheduled to decay

	private HashSet<Block> bfsDiscoveredSet; // general bfs discovered set
	private HashSet<Block> logHeads; // log heads are log blocks with leaves next to them

	private LinkedList<Block> bfsLeafQueue;
	private HashSet<Block> bfsDiscoveredLeafSet;
	
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
		bfsQueue = new LinkedList<Block>();
		bfsQueue.add(base);
		bfsDiscoveredSet = new HashSet<Block>();
		bfsDiscoveredSet.add(base);
		logHeads = new HashSet<Block>();
		
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
					boolean headHasLeaves = discoverSurroundingBlock(head.getRelative(i, j, k));
					if (headHasLeaves && !logHeads.contains(head)) {
						logHeads.add(head);
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

	private void decayTreeLeaves() {
		parentPlugin.getLogger().info("Decaying leaves");
		bfsDiscoveredSet = new HashSet<Block>();
//		nearByTrees = new LinkedList<Block>();

//		// search for nearby logs within a chunk radius
//		for (Block head : logHeads) {
//			for (int xOff = -8; xOff < 8; xOff++) {
//				for (int zOff = -8; zOff < 8; zOff++) {
//					Block relative = head.getRelative(xOff, 0, zOff);
//					if (relative.getType() == Material.LOG && isATree(relative)) {
//						nearByTrees.add(relative);
//					}
//				}
//			}
//		}

		// check if leaves are supported
		for (Block head : logHeads) {
			bfsDiscoverAdjacentLeaves(head, Material.LEAVES, Material.LEAVES_2);

			while (!bfsQueue.isEmpty()) {
				Block leaf = bfsQueue.removeFirst();
				
				bfsDiscoverAdjacentLeaves(leaf, Material.LEAVES, Material.LEAVES_2);
				
				scheduleLeafToDecay(leaf);  //TODO: this is what neeeds to change to decide which decays
				complexity++;	//TODO: remove
			}
		}

		// decay all leaves at once at the end
		decayLeaves();

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

	private void bfsDiscoverAdjacentLeaves(Block head, Material... toDiscover) {
		final int[] xOff = { 0, -1, 1, 0,  0, 0};
		final int[] yOff = {-1,  0, 0, 0,  0, 1};
		final int[] zOff = { 0,  0, 0, 1, -1, 0};
		
		for(int adjacent = 0; adjacent < 6; adjacent++) {
			bfsDiscoverAdjacentLeaf(head, xOff[adjacent], yOff[adjacent], zOff[adjacent], toDiscover);
		}
	}
	private void bfsDiscoverAdjacentLeaf(Block head, int xOff, int yOff, int zOff, Material... toDiscover) {
		Block neighbor = head.getRelative(xOff, yOff, zOff);
		if (!bfsDiscoveredSet.contains(neighbor)) {
			for (Material material : toDiscover) {
				if (neighbor.getType() == material) {
					bfsQueue.addLast(neighbor);
					bfsDiscoveredSet.add(neighbor);
					break;
				}
			}
		}
	}

	private boolean isNotALeaf(Block block) {
		return block.getType() != Material.LEAVES && block.getType() != Material.LEAVES_2;
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
		//if there are no logs nearby or if far enough
//		if(nearByTrees.size() == 0 || isLeafFarEnough(leaf)) {
//			//schedule decay
//			leavesToDecay.add(leaf);
//		}
//		Leaves leafs = ((Leaves)leaf.getState().getData());
//		leafs.setDecaying(true);
//		leaf.setData(leafs.getData());
//		Bukkit.getServer().getPluginManager().callEvent(new LeavesDecayEvent(leaf));
	}

	private boolean isLeafFarEnough(Block head) {
		//init new search
		bfsLeafQueue = new LinkedList<Block>();
		bfsDiscoveredLeafSet = new HashSet<Block>();
		leafRadi = new LinkedList<Integer>();
		
		bfsLeafQueue.add(head);
		leafRadi.add(0);
		
		while(!bfsLeafQueue.isEmpty()) {
			
			head = bfsQueue.removeFirst();
			
			bfsLeafDiscoverAdjacentBlocks(head, Material.LEAVES, Material.LEAVES_2);

			complexity++;
		}
		
		
		return true;
	}

	private void bfsLeafDiscoverAdjacentBlocks(Block head, Material... leaves) {
		
	}

	private boolean breakLeafBlock(Block blockToBreak) {
		// TODO: change
		if (!logNearBy(blockToBreak)) {
			blockToBreak.breakNaturally();
		}
		return true;
	}

	private boolean logNearBy(Block head) {

		return false;
	}
}
