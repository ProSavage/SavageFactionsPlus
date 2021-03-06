package com.massivecraft.factions.zcore.wands.impl;

import com.massivecraft.factions.Conf;
import com.massivecraft.factions.SavageFactions;
import com.massivecraft.factions.util.CraftKey;
import com.massivecraft.factions.util.ItemBuilder;
import com.massivecraft.factions.util.Placeholder;
import com.massivecraft.factions.util.Util;
import com.massivecraft.factions.zcore.nbtapi.NBTItem;
import com.massivecraft.factions.zcore.util.TL;
import com.massivecraft.factions.zcore.wands.Wand;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;

public class CondenseWand extends Wand {


   public CondenseWand(ItemStack wandItemStack, Player player, Chest chest) {
      this.wandItemStack = wandItemStack;
      this.player = player;
      this.chest = chest;
   }

   public static ItemStack buildItem(Integer uses) {
      ItemStack itemStack = Wand.buildItem();
      NBTItem nbtItem = new NBTItem(itemStack);
      nbtItem.setBoolean("Condense", true);
      if (uses == null) {
         uses = Conf.craftWandUses;
         nbtItem.setInteger("Uses", uses);
      } else {
         nbtItem.setInteger("Uses", uses);
      }
      itemStack = nbtItem.getItem();
      return new ItemBuilder(itemStack)
              .name(Conf.craftWandItemName)
              .lore(Util.colorWithPlaceholders(Conf.craftWandItemLore
                      , new Placeholder("{uses}", uses + "")))
              .build();
   }


   public static boolean isCondenseWand(ItemStack itemStack) {
      if (!Wand.isWand(itemStack)) {
         return false;
      }
      NBTItem nbtItem = new NBTItem(itemStack);
      return nbtItem.hasKey("Condense");
   }


   public void run() {
      Bukkit.getScheduler().runTaskAsynchronously(SavageFactions.plugin, () -> {
         HashMap<Material, CraftKey> craftKeys = Conf.craftKeys;
         chest.update();
         if (chest.getBlockInventory() == null) {
            updateWand();
         }
         HashMap<Material, Integer> validItems = new HashMap<>();
         Inventory inventory = chest.getBlockInventory();
         ArrayList<ItemStack> itemStacks = new ArrayList<>();
         for (ItemStack itemStack : inventory.getContents()) {
            if (itemStack == null) {
               continue;
            }
            Material itemType = itemStack.getType();
            if (!craftKeys.containsKey(itemType)) {
               itemStacks.add(itemStack);
               continue;
            }
            if (validItems.containsKey(itemType)) {
               validItems.put(itemType, validItems.get(itemType) + itemStack.getAmount());
            } else {
               validItems.put(itemType, itemStack.getAmount());
            }
         }
         for (Material material : validItems.keySet()) {

            CraftKey craftKey = craftKeys.get(material);
            int craftedAmount = (int) Math.floor(validItems.get(material) / craftKey.getAmountToResult());
            int remainder = validItems.get(material) % craftKey.getAmountToResult();
            int fullStacks = (int) Math.floor(craftedAmount / 64);
            int remainingStacks = craftedAmount % 64;
            for (int i = 0; i < fullStacks; i++) {
               wandUsed = true;
               itemStacks.add(new ItemStack(craftKey.getResult(), 64));
            }
            if (remainingStacks != 0) {
               wandUsed = true;
               itemStacks.add(new ItemStack(craftKey.getResult(), remainingStacks));
            }
            if (remainder != 0) {
               itemStacks.add(new ItemStack(craftKey.getInitial(), remainder));
            }


         }
         inventory.setContents(itemStacks.toArray(new ItemStack[itemStacks.size()]));
         chest.update();
         updateWand();
         if (wandUsed) {
            player.sendMessage(Util.color(TL.WAND_CRAFTED_ITEMS.toString()));
         }
      });
   }
}
