package com.example.scratch;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BuyCardCommand implements CommandExecutor, TabCompleter {

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
            player.sendMessage("§c可用种类: " + String.join(", ", plugin.getCardLoader().getCardNames()));
            return true;
        }

        // 从 CardLoader 获取卡片数据
        String typeName = args[0].toLowerCase();
        CardData cardData = plugin.getCardLoader().getCardData(typeName);
        if (cardData == null) {
            player.sendMessage("§c无效的卡种类！可用: " + String.join(", ", plugin.getCardLoader().getCardNames()));
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
        double totalCost = cardData.getPrice() * plugin.getMultiplier() * amount;

        // 检查余额
        if (!ScratchPlugin.getEconomy().has(player, totalCost)) {
            player.sendMessage("§c余额不足！需要 §e" + formatMoney(totalCost) + " 金币§c，你只有 §e"
                    + formatMoney(ScratchPlugin.getEconomy().getBalance(player)) + " 金币");
            return true;
        }

        // 扣钱
        ScratchPlugin.getEconomy().withdrawPlayer(player, totalCost);

        // 给卡
        ItemStack card = buildCard(cardData);
        card.setAmount(amount);
        player.getInventory().addItem(card);

        player.sendMessage("§a成功购买 §e" + amount + " 张 " + getCardDisplayName(typeName) + "§a，花费 §e"
                + formatMoney(totalCost) + " 金币");
        return true;
    }

    private ItemStack buildCard(CardData cardData) {
        String displayStr = cardData.getDisplay();
        List<String> lore = cardData.getLore();

        ItemStack card = new ItemStack(Material.PAPER);
        ItemMeta meta = card.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(displayStr));

        List<net.kyori.adventure.text.Component> compList = new ArrayList<>();
        for (String s : lore) {
            compList.add(MiniMessage.miniMessage().deserialize(s));
        }
        meta.lore(compList);

        NamespacedKey key = new NamespacedKey(plugin, "card_type");
        // 使用大写枚举名，与 CardType 保持一致
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, cardData.getName().toUpperCase());
        card.setItemMeta(meta);
        return card;
    }

    private String getCardDisplayName(String cardType) {
        return switch (cardType.toLowerCase()) {
            case "bronze" -> "铜刮刮卡";
            case "gold" -> "金刮刮卡";
            case "diamond" -> "钻石刮刮卡";
            default -> {
                CardData data = plugin.getCardLoader().getCardData(cardType);
                if (data != null) {
                    String display = data.getDisplay();
                    yield display.replaceAll("<[^>]+>", "");
                }
                yield cardType.substring(0, 1).toUpperCase() + cardType.substring(1) + "刮刮卡";
            }
        };
    }

    // ========== Tab 补全 ==========

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return List.of();

        if (args.length == 1) {
            // 补全卡片种类
            String partial = args[0].toLowerCase();
            return plugin.getCardLoader().getCardNames().stream()
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return List.of("<数量>");
        }

        return List.of();
    }

    private String formatMoney(double amount) {
        if (amount == (long) amount) return String.valueOf((long) amount);
        return String.format("%.1f", amount);
    }
}
