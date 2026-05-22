package com.example.scratch;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class BuyCardCommand implements CommandExecutor {

    private final ScratchPlugin plugin;

    public BuyCardCommand(ScratchPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该指令只能由玩家执行！");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage("§c用法: /buycard <种类> <数量>");
            player.sendMessage("§c种类: bronze, gold, diamond");
            return true;
        }

        CardType type;
        try {
            type = CardType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c无效的卡种类！请使用：bronze, gold, diamond");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage("§c数量必须是正整数！");
            return true;
        }

        // 计算价格
        String path = "cards." + type.name().toLowerCase() + ".";
        double basePrice = plugin.getConfig().getDouble(path + "price", 1.0);
        double multiplier = plugin.getMultiplier();
        double totalCost = basePrice * multiplier * amount;

        // 检查余额
        if (!ScratchPlugin.getEconomy().has(player, totalCost)) {
            player.sendMessage("§c余额不足！需要 §e" + formatMoney(totalCost) + " 金币§c，你只有 §e"
                    + formatMoney(ScratchPlugin.getEconomy().getBalance(player)) + " 金币");
            return true;
        }

        // 扣钱
        ScratchPlugin.getEconomy().withdrawPlayer(player, totalCost);

        // 给卡
        ItemStack card = buildCard(type);
        card.setAmount(amount);
        player.getInventory().addItem(card);

        player.sendMessage("§a成功购买 §e" + amount + " 张 " + getCardDisplayName(type) + "§a，花费 §e"
                + formatMoney(totalCost) + " 金币");
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

    private String getCardDisplayName(CardType type) {
        return switch (type) {
            case BRONZE -> "铜刮刮卡";
            case GOLD -> "金刮刮卡";
            case DIAMOND -> "钻石刮刮卡";
        };
    }

    private String formatMoney(double amount) {
        if (amount == (long) amount) {
            return String.valueOf((long) amount);
        }
        return String.format("%.1f", amount);
    }
}
