package com.massivecraft.factions.zcore.wands;

import com.massivecraft.factions.util.ItemBuilder;
import com.massivecraft.factions.util.Placeholder;
import com.massivecraft.factions.util.Util;
import com.massivecraft.factions.zcore.nbtapi.NBTItem;
import com.massivecraft.factions.zcore.util.TL;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public abstract class Wand {

   protected Chest chest;
   protected Player player;
   protected ItemStack wandItemStack;
   protected boolean wandUsed = false;


   public static ItemStack buildItem() {
      ItemStack itemStack = new ItemStack(Material.STICK, 1);
      NBTItem nbtItem = new NBTItem(itemStack);
      nbtItem.setBoolean("Wand", true);
      nbtItem.setInteger("ID", ThreadLocalRandom.current().nextInt(0, 1000));
      return nbtItem.getItem();
   }


   public static boolean isWand(ItemStack itemStack) {
      NBTItem nbtItem = new NBTItem(itemStack);
      return nbtItem.hasKey("Wand");
   }

   public void useDurability() {
      NBTItem nbtItem = new NBTItem(wandItemStack);
      nbtItem.setInteger("Uses", nbtItem.getInteger("Uses") - 1);
      if (nbtItem.getInteger("Uses") <= 0) {
         wandItemStack = new ItemStack(Material.AIR);
         return;
      } else {
         wandItemStack = nbtItem.getItem();
      }
      int usesLeft = new NBTItem(wandItemStack).getInteger("Uses");
      List<String> lore = wandItemStack.getItemMeta().getLore();
      this.wandItemStack = new ItemBuilder(wandItemStack).lore(Util.colorWithPlaceholders(lore, new Placeholder(((usesLeft + 1) + ""), usesLeft + ""))).build();
   }

   public void takeWand() {
      player.setItemInHand(new ItemStack(Material.AIR));
   }

   public void updateWand() {
      if (wandUsed) {
         useDurability();
      } else {
         player.sendMessage(Util.color(TL.WAND_WAND_NOT_USED.toString()));
      }
      wandItemStack.setAmount(1);
      player.getInventory().addItem(wandItemStack);
   }


   public abstract void run();


}
