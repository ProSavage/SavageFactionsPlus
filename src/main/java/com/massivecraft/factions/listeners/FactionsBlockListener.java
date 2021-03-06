package com.massivecraft.factions.listeners;

import com.massivecraft.factions.*;
import com.massivecraft.factions.integration.Worldguard;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.struct.Relation;
import com.massivecraft.factions.struct.Role;
import com.massivecraft.factions.util.MultiversionMaterials;
import com.massivecraft.factions.util.Particles.ParticleEffect;
import com.massivecraft.factions.zcore.fperms.Access;
import com.massivecraft.factions.zcore.fperms.PermissableAction;
import com.massivecraft.factions.zcore.nbtapi.NBTItem;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;


public class FactionsBlockListener implements Listener {

    public static HashMap<String, Location> bannerLocations = new HashMap<>();
    private HashMap<String, Boolean> bannerCooldownMap = new HashMap<>();


    public static boolean playerCanBuildDestroyBlock(Player player, Location location, String action, boolean justCheck) {
        String name = player.getName();
	    if (Conf.playersWhoBypassAllProtection.contains(name))
            return true;


        FPlayer me = FPlayers.getInstance().getById(player.getUniqueId().toString());
	    if (me.isAdminBypassing())
            return true;


        FLocation loc = new FLocation(location);
        Faction otherFaction = Board.getInstance().getFactionAt(loc);

        if (otherFaction.isWilderness()) {
	        if (Conf.worldGuardBuildPriority && Worldguard.playerCanBuild(player, location))
                return true;


	        if (!Conf.wildernessDenyBuild || Conf.worldsNoWildernessProtection.contains(location.getWorld().getName()))
                return true; // This is not faction territory. Use whatever you like here.


	        if (!justCheck)
                me.msg("<b>You can't " + action + " in the wilderness.");
            return false;
        } else if (otherFaction.isSafeZone()) {
	        if (Conf.worldGuardBuildPriority && Worldguard.playerCanBuild(player, location))
                return true;


	        if (!Conf.safeZoneDenyBuild || Permission.MANAGE_SAFE_ZONE.has(player))
                return true;


	        if (!justCheck)
                me.msg("<b>You can't " + action + " in a safe zone.");


            return false;
        } else if (otherFaction.isWarZone()) {
	        if (Conf.worldGuardBuildPriority && Worldguard.playerCanBuild(player, location))
                return true;


	        if (!Conf.warZoneDenyBuild || Permission.MANAGE_WAR_ZONE.has(player))
                return true;


	        if (!justCheck)
                me.msg("<b>You can't " + action + " in a war zone.");


            return false;
        }
	    if (SavageFactions.plugin.getConfig().getBoolean("hcf.raidable", false) && otherFaction.getLandRounded() >= otherFaction.getPowerRounded())
            return true;


        Faction myFaction = me.getFaction();
        Relation rel = myFaction.getRelationTo(otherFaction);
        boolean online = otherFaction.hasPlayersOnline();
        boolean pain = !justCheck && rel.confPainBuild(online);
        boolean deny = rel.confDenyBuild(online);

        Access access = otherFaction.getAccess(me, PermissableAction.fromString(action));
	    if (access == Access.ALLOW && ((rel == Relation.ALLY) || (rel == Relation.ENEMY) || (rel == Relation.NEUTRAL) || (rel == Relation.TRUCE)))
            deny = false;

        // hurt the player for building/destroying in other territory?
        if (pain) {
            player.damage(Conf.actionDeniedPainAmount);

            if (!deny) {
                me.msg("<b>It is painful to try to " + action + " in the territory of " + otherFaction.getTag(myFaction));
            }
        }


        // cancel building/destroying in other territory?
        if (deny) {
            if (!justCheck) {
                me.msg("<b>You can't " + action + " in the territory of " + otherFaction.getTag(myFaction));
            }

            return false;
        }

        // Also cancel and/or cause pain if player doesn't have ownership rights for this claim
        if (Conf.ownedAreasEnabled && (Conf.ownedAreaDenyBuild || Conf.ownedAreaPainBuild) && !otherFaction.playerHasOwnershipRights(me, loc)) {
            if (!pain && Conf.ownedAreaPainBuild && !justCheck) {
                player.damage(Conf.actionDeniedPainAmount);
                if (!Conf.ownedAreaDenyBuild) {
                    me.msg("<b>It is painful to try to " + action + " in this territory, it is owned by: " + otherFaction.getOwnerListString(loc));
                }
            }
            if (Conf.ownedAreaDenyBuild) {
                if (!justCheck) {
                    me.msg("<b>You can't " + action + " in this territory, it is owned by: " + otherFaction.getOwnerListString(loc));
                    return false;
                }
            }
        }

        // Check the permission just after making sure the land isn't owned by someone else to avoid bypass.

        if (access != Access.ALLOW && me.getRole() != Role.LEADER) {
            // TODO: Update this once new access values are added other than just allow / deny.
            if (access == Access.DENY) {
	            if (!justCheck)
		            me.msg(TL.GENERIC_NOPERMISSION, action);
                return false;
            } else if (myFaction.getOwnerListString(loc) != null && !myFaction.getOwnerListString(loc).isEmpty() && !myFaction.getOwnerListString(loc).contains(player.getName())) {
	            if (!justCheck)
		            me.msg("<b>You can't " + action + " in this territory, it is owned by: " + myFaction.getOwnerListString(loc));
                return false;
            }
        }
        return true;
    }

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.canBuild()) {
            return;
        }

        // special case for flint&steel, which should only be prevented by DenyUsage list
        if (event.getBlockPlaced().getType() == Material.FIRE) {
            return;
        }

        if (!playerCanBuildDestroyBlock(event.getPlayer(), event.getBlock().getLocation(), "build", false)) {
            event.setCancelled(true);
        }
    }

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!Conf.handleExploitLiquidFlow) {
            return;
        }
        if (event.getBlock().isLiquid()) {
            if (event.getToBlock().isEmpty()) {
                Faction from = Board.getInstance().getFactionAt(new FLocation(event.getBlock()));
                Faction to = Board.getInstance().getFactionAt(new FLocation(event.getToBlock()));
                if (from == to) {
                    // not concerned with inter-faction events
                    return;
                }
                // from faction != to faction
                if (to.isNormal()) {
                    if (from.isNormal() && from.getRelationTo(to).isAlly()) {
                        return;
                    }
                    event.setCancelled(true);
                }
            }
        }
    }

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (event.getInstaBreak() && !playerCanBuildDestroyBlock(event.getPlayer(), event.getBlock().getLocation(), "destroy", false)) {
            event.setCancelled(true);
        }
    }

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (!Conf.pistonProtectionThroughDenyBuild) {
            return;
        }

        Faction pistonFaction = Board.getInstance().getFactionAt(new FLocation(event.getBlock()));

        // target end-of-the-line empty (air) block which is being pushed into, including if piston itself would extend into air
        Block targetBlock = event.getBlock().getRelative(event.getDirection(), event.getLength() + 1);

        // if potentially pushing into air/water/lava in another territory, we need to check it out
        if ((targetBlock.isEmpty() || targetBlock.isLiquid()) && !canPistonMoveBlock(pistonFaction, targetBlock.getLocation())) {
            event.setCancelled(true);
        }

        /*
         * note that I originally was testing the territory of each affected block, but since I found that pistons can only push
         * up to 12 blocks and the width of any territory is 16 blocks, it should be safe (and much more lightweight) to test
         * only the final target block as done above
         */
    }

    @EventHandler
    public void onVaultPlace(BlockPlaceEvent e) {
        if (e.getItemInHand().getType() == Material.CHEST) {
            ItemStack vault = SavageFactions.plugin.createItem(Material.CHEST, 1, (short) 0, SavageFactions.plugin.color(SavageFactions.plugin.getConfig().getString("fvault.Item.Name")), SavageFactions.plugin.colorList(SavageFactions.plugin.getConfig().getStringList("fvault.Item.Lore")));
            if (e.getItemInHand().isSimilar(vault)) {
                FPlayer fme = FPlayers.getInstance().getByPlayer(e.getPlayer());
                if (fme.getFaction().getVault() != null) {
                    fme.msg(TL.COMMAND_GETVAULT_ALREADYSET);
                    e.setCancelled(true);
                    return;
                }
                FLocation flocation = new FLocation(e.getBlockPlaced().getLocation());
                if (Board.getInstance().getFactionAt(flocation) != fme.getFaction()) {
                    fme.msg(TL.COMMAND_GETVAULT_INVALIDLOCATION);
                    e.setCancelled(true);
                    return;
                }
                Block start = e.getBlockPlaced();
                int radius = 1;
                for (double x = start.getLocation().getX() - radius; x <= start.getLocation().getX() + radius; x++) {
                    for (double y = start.getLocation().getY() - radius; y <= start.getLocation().getY() + radius; y++) {
                        for (double z = start.getLocation().getZ() - radius; z <= start.getLocation().getZ() + radius; z++) {
                            Location blockLoc = new Location(e.getPlayer().getWorld(), x, y, z);
                            if (blockLoc.getX() == start.getLocation().getX() && blockLoc.getY() == start.getLocation().getY() && blockLoc.getZ() == start.getLocation().getZ()) {
                                continue;
                            }

                            Material blockMaterial = blockLoc.getBlock().getType();

                            if (blockMaterial == Material.CHEST || (SavageFactions.plugin.getConfig().getBoolean("fvault.No-Hoppers-near-vault") && blockMaterial == Material.HOPPER)) {
                                e.setCancelled(true);
                                fme.msg(TL.COMMAND_GETVAULT_CHESTNEAR);
                                return;
                            }
                        }
                    }
                }

                fme.msg(TL.COMMAND_GETVAULT_SUCCESS);
                fme.getFaction().setVault(e.getBlockPlaced().getLocation());

            }
        }
    }

    @EventHandler
    public void onHopperPlace(BlockPlaceEvent e) {

        if (e.getItemInHand().getType() != Material.HOPPER && !SavageFactions.plugin.getConfig().getBoolean("fvault.No-Hoppers-near-vault")) {
            return;
        }

        Faction factionAt = Board.getInstance().getFactionAt(new FLocation(e.getBlockPlaced().getLocation()));

        if (factionAt.isWilderness() || factionAt.getVault() == null) {
            return;
        }


        FPlayer fme = FPlayers.getInstance().getByPlayer(e.getPlayer());

        Block start = e.getBlockPlaced();
        int radius = 1;
        for (double x = start.getLocation().getX() - radius; x <= start.getLocation().getX() + radius; x++) {
            for (double y = start.getLocation().getY() - radius; y <= start.getLocation().getY() + radius; y++) {
                for (double z = start.getLocation().getZ() - radius; z <= start.getLocation().getZ() + radius; z++) {
                    Location blockLoc = new Location(e.getPlayer().getWorld(), x, y, z);
                    if (blockLoc.getX() == start.getLocation().getX() && blockLoc.getY() == start.getLocation().getY() && blockLoc.getZ() == start.getLocation().getZ()) {
                        continue;
                    }

                    if (blockLoc.getBlock().getType() == Material.CHEST) {
                        if (factionAt.getVault().equals(blockLoc)) {
                            e.setCancelled(true);
                            fme.msg(TL.COMMAND_VAULT_NO_HOPPER);
                            return;
                        }
                    }
                }
            }
        }

    }

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        // if not a sticky piston, retraction should be fine
        if (!event.isSticky() || !Conf.pistonProtectionThroughDenyBuild) {
            return;
        }

        Location targetLoc = event.getRetractLocation();
        Faction otherFaction = Board.getInstance().getFactionAt(new FLocation(targetLoc));

        // Check if the piston is moving in a faction's territory. This disables pistons entirely in faction territory.
        if (otherFaction.isNormal() && SavageFactions.plugin.getConfig().getBoolean("disable-pistons-in-territory", false)) {
            event.setCancelled(true);
            return;
        }

        // if potentially retracted block is just air/water/lava, no worries
        if (targetLoc.getBlock().isEmpty() || targetLoc.getBlock().isLiquid()) {
            return;
        }

        Faction pistonFaction = Board.getInstance().getFactionAt(new FLocation(event.getBlock()));

        if (!canPistonMoveBlock(pistonFaction, targetLoc)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBannerPlace(BlockPlaceEvent e) {
        if (SavageFactions.plugin.mc17) {
            return;
        }

        if (e.getItemInHand().getType() == SavageFactions.plugin.BANNER) {
            ItemStack bannerInHand = e.getItemInHand();
            FPlayer fme = FPlayers.getInstance().getByPlayer(e.getPlayer());
            ItemStack warBanner = fme.getFaction().getBanner();
            if (warBanner != null) {
                ItemMeta warmeta = warBanner.getItemMeta();
                warmeta.setDisplayName(SavageFactions.plugin.color(SavageFactions.plugin.getConfig().getString("fbanners.Item.Name")));
                warmeta.setLore(SavageFactions.plugin.colorList(SavageFactions.plugin.getConfig().getStringList("fbanners.Item.Lore")));
                warBanner.setItemMeta(warmeta);
            } else {
                warBanner = SavageFactions.plugin.createItem(SavageFactions.plugin.BANNER, 1, (short) 1, SavageFactions.plugin.getConfig().getString("fbanners.Item.Name"), SavageFactions.plugin.getConfig().getStringList("fbanners.Item.Lore"));
            }
            if (warBanner.isSimilar(bannerInHand)) {

                if (fme.getFaction().isWilderness()) {
                    fme.msg(TL.WARBANNER_NOFACTION);
                    e.setCancelled(true);
                    return;
                }
                int bannerTime = SavageFactions.plugin.getConfig().getInt("fbanners.Banner-Time") * 20;

                Location placedLoc = e.getBlockPlaced().getLocation();
                FLocation fplacedLoc = new FLocation(placedLoc);
                if ((Board.getInstance().getFactionAt(fplacedLoc).isWarZone() && SavageFactions.plugin.getConfig().getBoolean("fbanners.Placeable.Warzone"))
                        || (fme.getFaction().getRelationTo(Board.getInstance().getFactionAt(fplacedLoc)) == Relation.ENEMY) && SavageFactions.plugin.getConfig().getBoolean("fbanners.Placeable.Enemy")) {
                    if (bannerCooldownMap.containsKey(fme.getTag())) {
                        fme.msg(TL.WARBANNER_COOLDOWN);
                        e.setCancelled(true);
                        return;
                    }
                    for (FPlayer fplayer : fme.getFaction().getFPlayers()) {
                        //  if (fplayer == fme) { continue; }   //Idk if I wanna not send the title to the player
	                    fplayer.getPlayer().sendTitle(SavageFactions.plugin.color(fme.getTag() + " Placed A WarBanner!"), SavageFactions.plugin.color("&7use &c/f tpbanner&7 to tp to the banner!"), 10, 70, 20);

                    }
                    bannerCooldownMap.put(fme.getTag(), true);
                    bannerLocations.put(fme.getTag(), e.getBlockPlaced().getLocation());
                    final int bannerCooldown = SavageFactions.plugin.getConfig().getInt("fbanners.Banner-Place-Cooldown");
                    final ArmorStand as = (ArmorStand) e.getBlockPlaced().getLocation().add(0.5, 1, 0.5).getWorld().spawnEntity(e.getBlockPlaced().getLocation().add(0.5, 1, 0.5), EntityType.ARMOR_STAND); //Spawn the ArmorStand
                    as.setVisible(false); //Makes the ArmorStand invisible
                    as.setGravity(false); //Make sure it doesn't fall
                    as.setCanPickupItems(false); //I'm not sure what happens if you leave this as it is, but you might as well disable it
                    as.setCustomName(SavageFactions.plugin.color(SavageFactions.plugin.getConfig().getString("fbanners.BannerHolo").replace("{Faction}", fme.getTag()))); //Set this to the text you want
                    as.setCustomNameVisible(true); //This makes the text appear no matter if your looking at the entity or not
                    final ArmorStand armorStand = as;
                    final String tag = fme.getTag();
                    Bukkit.getScheduler().scheduleSyncDelayedTask(SavageFactions.plugin, new Runnable() {
                        @Override
                        public void run() {
                            bannerCooldownMap.remove(tag);
                        }
                    }, Long.parseLong(bannerCooldown + ""));
                    final Block banner = e.getBlockPlaced();
                    final Material bannerType = banner.getType();
                    final Faction bannerFaction = fme.getFaction();
                    banner.getWorld().strikeLightningEffect(banner.getLocation());
                    //  e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ENTITY_LIGHTNING_IMPACT,2.0F,0.5F);
                    final int radius = SavageFactions.plugin.getConfig().getInt("fbanners.Banner-Effect-Radius");
                    final List<String> effects = SavageFactions.plugin.getConfig().getStringList("fbanners.Effects");
                    final int affectorTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(SavageFactions.plugin, new Runnable() {
                        @Override
                        public void run() {

                            for (Entity e : banner.getLocation().getWorld().getNearbyEntities(banner.getLocation(), radius, 255, radius)) {
                                if (e instanceof Player) {
                                    Player player = (Player) e;
                                    FPlayer fplayer = FPlayers.getInstance().getByPlayer(player);
                                    if (fplayer.getFaction() == bannerFaction) {
                                        for (String effect : effects) {
                                            String[] components = effect.split(":");
                                            player.addPotionEffect(new PotionEffect(PotionEffectType.getByName(components[0]), 100, Integer.parseInt(components[1])));
                                        }
                                        ParticleEffect.LAVA.display(1, 1, 1, 1, 10, banner.getLocation(), 16);
                                        ParticleEffect.FLAME.display(1, 1, 1, 1, 10, banner.getLocation(), 16);

                                        if (banner.getType() != bannerType) {
                                            banner.setType(bannerType);
                                        }
                                    }
                                }
                            }
                        }
                    }, 0L, 20L);
                    Bukkit.getScheduler().scheduleSyncDelayedTask(SavageFactions.plugin, new Runnable() {
                        @Override
                        public void run() {
                            banner.setType(Material.AIR);
                            as.remove();
                            banner.getWorld().strikeLightningEffect(banner.getLocation());
                            Bukkit.getScheduler().cancelTask(affectorTask);
                            bannerLocations.remove(bannerFaction.getTag());
                        }
                    }, Long.parseLong(bannerTime + ""));
                } else {
                    fme.msg(TL.WARBANNER_INVALIDLOC);
                    e.setCancelled(true);
                }
            }
        }
    }

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFrostWalker(EntityBlockFormEvent event) {
        if (event.getEntity() == null || event.getEntity().getType() != EntityType.PLAYER || event.getBlock() == null) {
            return;
        }

        Player player = (Player) event.getEntity();
        Location location = event.getBlock().getLocation();

        // only notify every 10 seconds
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);
        boolean justCheck = fPlayer.getLastFrostwalkerMessage() + 10000 > System.currentTimeMillis();
        if (!justCheck) {
            fPlayer.setLastFrostwalkerMessage();
        }

        // Check if they have build permissions here. If not, block this from happening.
        if (!playerCanBuildDestroyBlock(player, location, "frostwalk", justCheck)) {
            event.setCancelled(true);
        }
    }

    private boolean canPistonMoveBlock(Faction pistonFaction, Location target) {

        Faction otherFaction = Board.getInstance().getFactionAt(new FLocation(target));

        if (pistonFaction == otherFaction) {
            return true;
        }

        if (otherFaction.isWilderness()) {
            return !Conf.wildernessDenyBuild || Conf.worldsNoWildernessProtection.contains(target.getWorld().getName());

        } else if (otherFaction.isSafeZone()) {
            return !Conf.safeZoneDenyBuild;

        } else if (otherFaction.isWarZone()) {
            return !Conf.warZoneDenyBuild;

        }

        Relation rel = pistonFaction.getRelationTo(otherFaction);

        return !rel.confDenyBuild(otherFaction.hasPlayersOnline());
    }

	@EventHandler
	public void onFarmLandDamage(EntityChangeBlockEvent event) {
		if (event.getEntity() instanceof Player) {
			Player player = (Player) event.getEntity();
			if (!playerCanBuildDestroyBlock(player, event.getBlock().getLocation(), PermissableAction.DESTROY.name(), true)) {
				FPlayer me = FPlayers.getInstance().getById(player.getUniqueId().toString());
				Faction otherFaction = Board.getInstance().getFactionAt(new FLocation(event.getBlock().getLocation()));
				Faction myFaction = me.getFaction();

				me.msg("<b>You can't jump on farmland in the territory of " + otherFaction.getTag(myFaction));
				event.setCancelled(true);
			}
		}
	}

	@SuppressWarnings("deprecation")
	private void onTrench(BlockBreakEvent event) {
		if (event.getPlayer().getItemInHand() == null
				  || (event.getPlayer().getItemInHand().getType() != MultiversionMaterials.DIAMOND_PICKAXE.parseMaterial()
				  && event.getPlayer().getItemInHand().getType() != MultiversionMaterials.DIAMOND_SHOVEL.parseMaterial())
				  || !new NBTItem(event.getPlayer().getItemInHand()).hasKey("trench")) {
			return;
		}
		Player player = event.getPlayer();
		Faction faction = FPlayers.getInstance().getByPlayer(event.getPlayer()).getFaction();
		Faction wilderness = Factions.getInstance().getWilderness();
		NBTItem nbtItem = new NBTItem(event.getPlayer().getItemInHand());
		int radius = nbtItem.getInteger("radius");
		int goesInto = radius / 2;
		radius = radius - (goesInto + 1);
		Block block = event.getBlock();
		for (double x = block.getLocation().getX() - radius; x <= block.getLocation().getX() + radius; x++) {
			for (double y = block.getLocation().getY() - radius; y <= block.getLocation().getY() + radius; y++) {
				for (double z = block.getLocation().getZ() - radius; z <= block.getLocation().getZ() + radius; z++) {
					Location loc = new Location(block.getWorld(), x, y, z);
					if (loc.getBlock().getType() == Material.BEDROCK
							  || loc.getBlock().getType() == Material.AIR
							  || Conf.trenchIgnoredBlocks.contains(loc.getBlock().getType().toString())
							  || Worldguard.playerCanBuild(player, loc)) {
						continue;
					}
					Faction factionAt = (Board.getInstance().getFactionAt(new FLocation(loc)));
					if (factionAt.equals(wilderness) || factionAt.equals(faction)) {
						event.getPlayer().getInventory().addItem(new ItemStack(loc.getBlock().getType()));
						loc.getBlock().setType(Material.AIR);
					}
				}
			}
		}


	}


	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!playerCanBuildDestroyBlock(event.getPlayer(), event.getBlock().getLocation(), "destroy", false)) {
            event.setCancelled(true);
            return;
        }
        FPlayer fme = FPlayers.getInstance().getByPlayer(event.getPlayer());
        onTrench(event);
        if (!fme.hasFaction()) {
            return;
        }
        if (event.getBlock().getType() == SavageFactions.plugin.MOB_SPANWER) {
            if (!fme.isAdminBypassing()) {
                Access access = fme.getFaction().getAccess(fme, PermissableAction.SPAWNER);
                if (access != Access.ALLOW && fme.getRole() != Role.LEADER) {
                    fme.msg(TL.GENERIC_FPERM_NOPERMISSION, "mine spawners");
                    return;
                }
            }
        }
    }
}
