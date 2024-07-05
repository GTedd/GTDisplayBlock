package me.c7dev.tensegrity;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.c7dev.tensegrity.api.events.PlayerClickBlockDisplayEvent;
import me.c7dev.tensegrity.displays.DexterityDisplay;
import me.c7dev.tensegrity.displays.animation.Animation;
import me.c7dev.tensegrity.displays.animation.RideAnimation;
import me.c7dev.tensegrity.util.ClickedBlockDisplay;
import me.c7dev.tensegrity.util.DexBlock;
import me.c7dev.tensegrity.util.DexTransformation;
import me.c7dev.tensegrity.util.DexUtils;

public class EventListeners implements Listener {
	
	private Dexterity plugin;
	private HashMap<UUID, Long> click_delay = new HashMap<>();
	
	public EventListeners(Dexterity plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}
	
	public boolean clickDelay(UUID u) {
		int delay = 100;
		if (System.currentTimeMillis() - click_delay.getOrDefault(u, 0l) < delay) return true;
		final long newdelay = System.currentTimeMillis() + delay;
		click_delay.put(u, newdelay);
		new BukkitRunnable() {
			@Override
			public void run() {
				if (click_delay.getOrDefault(u, 0l) == newdelay) click_delay.remove(u);
			}
		}.runTaskLater(plugin, (int) (delay*0.02));
		return false;
	}
		
	@EventHandler
	public void onBlockClick(PlayerInteractEvent e) {
				
		//DexterityDisplay disp = plugin.getClickedDisplay(e.getPlayer());
		//if (disp != null) Bukkit.getPluginManager().callEvent(new PlayerClickBlockDisplayEvent(e.getPlayer(), disp));
		
		if (e.getPlayer().hasPermission("dexterity.command")) {	
			
			if (clickDelay(e.getPlayer().getUniqueId())) return;
			
			//calculate if player clicked a block display
			ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
			ClickedBlockDisplay clicked = plugin.getAPI().getLookingAt(e.getPlayer());
			DexSession session = plugin.getEditSession(e.getPlayer().getUniqueId());
			DexterityDisplay clicked_display = null;
			DexBlock clicked_db = null;
			
			if (clicked != null) {
				clicked_db = plugin.getMappedDisplay(clicked.getBlockDisplay().getUniqueId());
				if (clicked_db != null) clicked_display = clicked_db.getDexterityDisplay();
			}
					
			//wand click
			if (hand.getType() == Material.WOODEN_AXE || (hand.getType() == Material.BLAZE_ROD && hand.getItemMeta().getDisplayName().equals(plugin.getConfigString("wand-title", "§fDexterity Wand")))) {
				if (session == null) session = new DexSession(e.getPlayer(), plugin);
				e.setCancelled(true);
				
				//select display with wand
				if (clicked_display != null && clicked_display.getLabel() != null) {
					session.setSelected(clicked_display, true);
					return;
				}
				if (clicked != null) { //click block with wand (set pos1 or pos2)
					if (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK) session.setLocation(DexUtils.blockLoc(clicked.getDisplayCenterLocation()), true);
					else session.setLocation(DexUtils.blockLoc(clicked.getDisplayCenterLocation()), false);
					if (session.getLocation1() == null || session.getLocation2() == null) plugin.getAPI().tempHighlight(clicked.getBlockDisplay(), 15, Color.GRAY);
				} else if (e.getClickedBlock() != null) {
					if (e.getAction() == Action.LEFT_CLICK_BLOCK) session.setLocation(e.getClickedBlock().getLocation(), true); //pos1
					else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) session.setLocation(e.getClickedBlock().getLocation(), false); //pos2
				}
			} else {
				if (clicked == null) return;
				e.setCancelled(true);
				
				//send event
				PlayerClickBlockDisplayEvent click_event = new PlayerClickBlockDisplayEvent(e.getPlayer(), clicked, e.getAction(), clicked_display);
				Bukkit.getPluginManager().callEvent(click_event);
				if (click_event.isCancelled()) return;
								
				//place a block display
				if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_AIR) {
					if (hand.getType() != Material.AIR) {
						
						BlockData bdata = Bukkit.createBlockData(hand.getType() == Material.NETHER_STAR ? Material.NETHER_PORTAL : hand.getType());
						Vector placingDimensions = DexUtils.getBlockDimensions(bdata);
						placingDimensions.setY(1); //account for block's y axis asymmetry

						Vector blockscale = DexUtils.vector(clicked.getBlockDisplay().getTransformation().getScale());
						Vector blockdimensions = DexUtils.getBlockDimensions(clicked.getBlockDisplay().getBlock());
						//Vector placingScale = DexUtils.hadimard(blockscale, DexUtils.getBlockDimensions(bdata));
						
						//calculate dimensions of clicked block display
						Vector avgPlaceDimensions = blockdimensions.clone().add(placingDimensions).multiply(0.5);
						Vector dimensions = DexUtils.hadimard(blockscale, avgPlaceDimensions);
						
						Vector dir = clicked.getNormal();
						Vector delta = dir.clone().multiply(Math.abs(DexUtils.faceToDirection(clicked.getBlockFace(), dimensions)));
						
						DexTransformation trans = (clicked_db == null ? new DexTransformation(clicked.getBlockDisplay().getTransformation()) : clicked_db.getTransformation());

						Location fromLoc = clicked.getDisplayCenterLocation();
						if (clicked.getBlockFace() != BlockFace.UP && clicked.getBlockFace() != BlockFace.DOWN) fromLoc.add(clicked.getUpDir().multiply((blockscale.getY()/2)*(1 - blockdimensions.getY())));
												
						BlockDisplay b = e.getPlayer().getWorld().spawn(fromLoc.add(delta), BlockDisplay.class, a -> {
							a.setBlock(bdata);
							trans.setScale(blockscale);
							trans.setDisplacement(blockscale.clone().multiply(-0.5));
							a.setTransformation(trans.build());
						});
												
						e.getPlayer().playSound(b.getLocation(), bdata.getSoundGroup().getPlaceSound(), 1f, 1f);

						if (clicked_display != null) clicked_display.getBlocks().add(new DexBlock(b, clicked_display));
					} else if (clicked_display != null) {
						//clicked display with empty hand
						RideAnimation ride = null;
						for (Animation a : clicked_display.getAnimations()) {
							if (a instanceof RideAnimation) {
								ride = (RideAnimation) a;
								break;
							}
						}
						if (ride != null && ride.getMountedPlayer() == null) {
							ride.mount(e.getPlayer());
							ride.start();
						}
					}
					
				} else { //break a block display
					e.getPlayer().playSound(clicked.getBlockDisplay().getLocation(), clicked.getBlockDisplay().getBlock().getSoundGroup().getBreakSound(), 1f, 1f);
					if (clicked_db == null) clicked.getBlockDisplay().remove();
					else clicked_db.remove();
				}
			}
		}
	}
	
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		DexSession session = plugin.getEditSession(e.getPlayer().getUniqueId());
		if (session == null || !session.isFollowing() || session.getSelected() == null) return;
		if (!session.getSelected().getCenter().getWorld().getName().equals(e.getPlayer().getWorld().getName())) {
			session.cancelEdit();
			session.setSelected(null, false);
			return;
		}
		
		Location loc = e.getPlayer().getLocation();
		if (!e.getPlayer().isSneaking()) loc = DexUtils.blockLoc(loc); //block location
		else loc.add(-0.5, 0, -0.5); //precise location
		
		loc.add(session.getFollowingOffset());
		
		Location center = session.getSelected().getCenter();
		if (loc.getX() == center.getX() && loc.getY() == center.getY() && loc.getZ() == center.getZ()) return;
		
		double cutoff = 0.01; //follow player
		if (Math.abs(e.getTo().getX() - e.getFrom().getX()) > cutoff || Math.abs(e.getTo().getY() - e.getFrom().getY()) > cutoff || Math.abs(e.getTo().getZ() - e.getFrom().getZ()) > cutoff) {
			session.getSelected().teleport(loc);
		}
	}

}
