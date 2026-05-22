package com.example.scratch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class CardMenuListener implements Listener, CommandExecutor {

    private final ScratchPlugin plugin;
    private final NamespacedKey cardTypeKey;
    private final NamespacedKey actionKey;
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    private static final int[] DISPLAY_SLOTS = {11, 13, 15, 38, 40, 42};
    private static final int PREV_BUTTON_SLOT = 45;
    private static final int NEXT_BUTTON_SLOT = 53;

    private static final Set<Integer> BORDER_SLOTS = new HashSet<>();
    static {
        for (int i = 0; i < 9; i++) BORDER_SLOTS.add(i);
        for (int i = 45; i < 54; i++) BORDER_SLOTS.add(i);
        for (int i = 9; i < 45; i += 9) BORDER_SLOTS.add(i);
        for (int i = 17; i < 45; i += 9) BORDER_SLOTS.add(i);
    }

    public CardMenuListener(ScratchPlugin plugin) {
        this.plugin = plugin;
        this.cardTypeKey = new NamespacedKey(plugin, "shop_card_type");
        this.actionKey = new NamespacedKey(plugin, "shop_action");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c该指令只能由玩家执行！");
            return true;
        }
        openMenu(player);
        return true;
    }

    public void openMenu(Player player) {
        List<String> cardNames = plugin.getCardLoader().getCardNames();
        if (cardNames.isEmpty()) {
            player.sendMessage("§c当前没有可用的刮刮卡！");
            return;
        }
        playerPages.put(player.getUniqueId(), 0);
        openPage(player, 0);
    }

    private void openPage(Player player, int page) {
        List<String> cardNames = plugin.getCardLoader().getCardNames();
        int totalPages = (int) Math.ceil((double) cardNames.size() / 6.0);
        if (totalPages < 1) totalPages = 1;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        playerPages.put(player.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(null, 54,
                MiniMessage.miniMessage().deserialize("<gradient:#FFD700:#FFA500>刮刮卡商店</gradient>"));

        fillBorder(inv, page, totalPages);

        int startIndex = page * 6;
        for (int i = 0; i < 6 && (startIndex + i) < cardNames.size(); i++) {
            String cardName = cardNames.get(startIndex + i);
            CardData cardData = plugin.getCardLoader().getCardData(cardName);
            if (cardData != null) {
                ItemStack displayItem = buildDisplayItem(cardData);
                inv.setItem(DISPLAY_SLOTS[i], displayItem);
            }
        }

        player.openInventory(inv);
    }

    private void fillBorder(Inventory inv, int currentPage, int totalPages) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.displayName(Component.text(" "));
        border.setItemMeta(borderMeta);

        for (int slot : BORDER_SLOTS) {
            inv.setItem(slot, border);
        }

        if (currentPage > 0) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.displayName(Component.text("§e上一页"));
            prevMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "prev");
            prevButton.setItemMeta(prevMeta);
            inv.setItem(PREV_BUTTON_SLOT, prevButton);
        }

        if (currentPage < totalPages - 1) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.displayName(Component.text("§e下一页"));
            nextMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "next");
            nextButton.setItemMeta(nextMeta);
            inv.setItem(NEXT_BUTTON_SLOT, nextButton);
        }
    }

    private ItemStack buildDisplayItem(CardData cardData) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(cardData.getDisplay()));

        List<Component> loreComponents = new ArrayList<>();
        for (String s : cardData.getLore()) {
            loreComponents.add(MiniMessage.miniMessage().deserialize(s));
        }

        double actualPrice = cardData.getPrice() * plugin.getMultiplier();
        loreComponents.add(Component.text(""));
        loreComponents.add(Component.text("§e价格: " + formatMoney(actualPrice) + " 金币"));
        loreComponents.add(Component.text("§a左键点击购买"));

        meta.lore(loreComponents);
        meta.getPersistentDataContainer().set(cardTypeKey, PersistentDataType.STRING, cardData.getName());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!playerPages.containsKey(uuid)) return;

        event.setCancelled(true);

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action != null) {
            int currentPage = playerPages.getOrDefault(uuid, 0);
            if (action.equals("prev")) openPage(player, currentPage - 1);
            else if (action.equals("next")) openPage(player, currentPage + 1);
            return;
        }

        String cardType = meta.getPersistentDataContainer().get(cardTypeKey, PersistentDataType.STRING);
        if (cardType != null && event.isLeftClick()) {
            purchaseCard(player, cardType);
        }
    }

    private void purchaseCard(Player player, String cardType) {
        CardData cardData = plugin.getCardLoader().getCardData(cardType);
        if (cardData == null) {
            player.sendMessage("§c该刮刮卡配置已失效！");
            return;
        }

        double actualPrice = cardData.getPrice() * plugin.getMultiplier();

        if (!ScratchPlugin.getEconomy().has(player, actualPrice)) {
            player.sendMessage("§c余额不足！需要 §e" + formatMoney(actualPrice) + " 金币§c，你只有 §e"
                    + formatMoney(ScratchPlugin.getEconomy().getBalance(player)) + " 金币");
            return;
        }

        ScratchPlugin.getEconomy().withdrawPlayer(player, actualPrice);

        ItemStack card = buildPhysicalCard(cardData);
        card.setAmount(1);
        player.getInventory().addItem(card);

        player.sendMessage("§a成功购买 §e1 张 " + getCardDisplayName(cardData) + "§a，花费 §e"
                + formatMoney(actualPrice) + " 金币");
    }

    private ItemStack buildPhysicalCard(CardData cardData) {
        ItemStack card = new ItemStack(Material.PAPER);
        ItemMeta meta = card.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(cardData.getDisplay()));

        List<Component> compList = new ArrayList<>();
        for (String s : cardData.getLore()) {
            compList.add(MiniMessage.miniMessage().deserialize(s));
        }
        meta.lore(compList);

        NamespacedKey key = new NamespacedKey(plugin, "card_type");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, cardData.getName().toUpperCase());
        card.setItemMeta(meta);
        return card;
    }

    private String getCardDisplayName(CardData cardData) {
        String display = cardData.getDisplay();
        return display.replaceAll("<[^>]+>", "");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            UUID uuid = event.getPlayer().getUniqueId();
            if (event.getPlayer() instanceof Player player) {
                if (!player.getOpenInventory().getTitle().contains("刮刮卡商店")) {
                    playerPages.remove(uuid);
                }
            }
        });
    }

    private String formatMoney(double amount) {
        if (amount == (long) amount) return String.valueOf((long) amount);
        return String.format("%.1f", amount);
    }
}
