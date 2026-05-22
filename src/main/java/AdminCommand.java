package com.example.scratch;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

public class AdminCommand implements CommandExecutor {

    private final ScratchPlugin plugin;

    public AdminCommand(ScratchPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("scratchcard.admin")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /getcard <玩家> <种类> <数量>");
            sender.sendMessage("§c种类: bronze, gold, diamond");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§c玩家不在线！");
            return true;
        }

        CardType type;
        try {
            type = CardType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的卡种类！请使用：bronze, gold, diamond");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage("§c数量必须是正整数！");
            return true;
        }

        ItemStack card = buildCard(type);
        card.setAmount(amount);
        target.getInventory().addItem(card);
        sender.sendMessage("§a已给予 " + target.getName() + " " + amount + " 张 " + type.name() + " 刮刮卡。");
        return true;
    }

    private ItemStack buildCard(CardType type) {
        String path = "cards." + type.name().toLowerCase() + ".";
        String displayStr = plugin.getConfig().getString(path + "display");
        List<String> lore = plugin.getConfig().getStringList(path + "lore");

        ItemStack card = new ItemStack(Material.PAPER);
        ItemMeta meta = card.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(displayStr));

        List<net.kyori.adventure.text.Component> compList = new ArrayList<>();
        for (String s : lore) {
            compList.add(MiniMessage.miniMessage().deserialize(s));
        }
        meta.lore(compList);

        NamespacedKey key = new NamespacedKey(plugin, "card_type");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.name());
        card.setItemMeta(meta);
        return card;
    }
}