package com.example.scratch;

import org.bukkit.Bukkit;
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
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {

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
            sender.sendMessage("§c可用种类: " + String.join(", ", plugin.getCardLoader().getCardNames()));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§c玩家不在线！");
            return true;
        }

        // 支持用 CardType 枚举名或配置文件名
        CardType type = null;
        String typeInput = args[1].toUpperCase();
        try {
            type = CardType.valueOf(typeInput);
        } catch (IllegalArgumentException e) {
            // 尝试通过文件名查找
            for (CardType ct : CardType.values()) {
                if (ct.name().equalsIgnoreCase(args[1])) {
                    type = ct;
                    break;
                }
            }
            if (type == null) {
                sender.sendMessage("§c无效的卡种类！可用: " + String.join(", ", plugin.getCardLoader().getCardNames()));
                return true;
            }
        }

        // 检查该卡片是否有配置数据
        if (plugin.getCardLoader().getCardData(type) == null) {
            sender.sendMessage("§c该刮刮卡没有有效的配置文件！");
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
        CardData cardData = plugin.getCardLoader().getCardData(type);
        if (cardData == null) return null;

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
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.name());
        card.setItemMeta(meta);
        return card;
    }

    // ========== Tab 补全 ==========

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("scratchcard.admin")) return List.of();

        if (args.length == 1) {
            // 补全在线玩家
            String partial = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            // 补全卡片种类（从 CardLoader 获取）
            String partial = args[1].toLowerCase();
            return plugin.getCardLoader().getCardNames().stream()
                    .filter(name -> name.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            return List.of("<数量>");
        }

        return List.of();
    }
}
