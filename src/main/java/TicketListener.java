package com.example.scratch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class TicketListener implements Listener {

    private final ScratchPlugin plugin;
    private final NamespacedKey cardTypeKey;
    private final Map<UUID, CardSession> sessions = new HashMap<>();

    // 奖励类型 → 显示物品 固定映射
    private static final Map<String, Material> REWARD_MATERIAL_MAP = new HashMap<>();
    static {
        REWARD_MATERIAL_MAP.put("EMPTY", Material.BARRIER);
        REWARD_MATERIAL_MAP.put("SMALL", Material.COPPER_INGOT);
        REWARD_MATERIAL_MAP.put("MEDIUM", Material.GOLD_INGOT);
        REWARD_MATERIAL_MAP.put("LARGE", Material.DIAMOND);
        REWARD_MATERIAL_MAP.put("HUGE", Material.EMERALD);
    }

    // 倍率值 → 显示物品 固定映射
    private static final Map<Integer, Material> MULTIPLIER_MATERIAL_MAP = new HashMap<>();
    static {
        MULTIPLIER_MATERIAL_MAP.put(1, Material.QUARTZ);
        MULTIPLIER_MATERIAL_MAP.put(2, Material.AMETHYST_SHARD);
        MULTIPLIER_MATERIAL_MAP.put(3, Material.NETHERITE_SCRAP);
        MULTIPLIER_MATERIAL_MAP.put(4, Material.NETHER_STAR);
        MULTIPLIER_MATERIAL_MAP.put(5, Material.DRAGON_EGG);
    }

    public TicketListener(ScratchPlugin plugin) {
        this.plugin = plugin;
        this.cardTypeKey = new NamespacedKey(plugin, "card_type");
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.PAPER) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        String typeStr = meta.getPersistentDataContainer().get(cardTypeKey, PersistentDataType.STRING);
        if (typeStr == null) return;

        CardType type;
        try {
            type = CardType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        CardData cardData = plugin.getCardLoader().getCardData(type);
        if (cardData == null) {
            event.getPlayer().sendMessage("§c该刮刮卡配置已失效！");
            return;
        }

        event.setCancelled(true);

        // 消耗手中一张卡
        item.setAmount(item.getAmount() - 1);
        event.getPlayer().getInventory().setItemInMainHand(item);

        openCard(event.getPlayer(), cardData);
    }

    /**
     * 打开刮刮卡界面
     */
    private void openCard(Player player, CardData cardData) {
        int size = cardData.getUiSize();
        String titleRaw = cardData.getUiTitle();
        int[] buttonSlots = cardData.getSlotPositions();
        int rewardSlotCount = cardData.getRewardSlots();

        Inventory inv = Bukkit.createInventory(null, size,
                MiniMessage.miniMessage().deserialize(titleRaw));

        fillBackground(inv, cardData);

        Random random = new Random();
        CardSession session = new CardSession(cardData, buttonSlots.length);

        // 生成奖励槽结果
        for (int i = 0; i < rewardSlotCount; i++) {
            int slot = buttonSlots[i];
            String rewardKey = getRandomReward(cardData, random);
            session.rewardResults.add(rewardKey);

            ItemStack bedrock = new ItemStack(Material.BEDROCK);
            ItemMeta bMeta = bedrock.getItemMeta();
            bMeta.displayName(Component.text("§f§l?"));
            bedrock.setItemMeta(bMeta);
            inv.setItem(slot, bedrock);
        }

        // 生成倍率槽结果
        for (int i = rewardSlotCount; i < buttonSlots.length; i++) {
            int slot = buttonSlots[i];
            int multiplierValue = getRandomMultiplier(cardData, random);
            session.multiplierResults.add(multiplierValue);

            ItemStack bedrock = new ItemStack(Material.BEDROCK);
            ItemMeta bMeta = bedrock.getItemMeta();
            bMeta.displayName(Component.text("§f§l?"));
            bedrock.setItemMeta(bMeta);
            inv.setItem(slot, bedrock);
        }

        player.openInventory(inv);
        sessions.put(player.getUniqueId(), session);

        // 打开音效（从配置读取）
        playSound(player, cardData.getSounds().getOpen(), 0.5f, 1.0f);
    }

    /**
     * 重新打开界面（保留已刮进度）
     */
    private void reopenCard(Player player, CardSession session) {
        CardData cardData = session.cardData;
        int size = cardData.getUiSize();
        String titleRaw = cardData.getUiTitle();
        int[] buttonSlots = cardData.getSlotPositions();
        int rewardSlotCount = cardData.getRewardSlots();

        Inventory inv = Bukkit.createInventory(null, size,
                MiniMessage.miniMessage().deserialize(titleRaw));

        fillBackground(inv, cardData);

        double globalMult = plugin.getMultiplier();

        for (int i = 0; i < buttonSlots.length; i++) {
            int slot = buttonSlots[i];

            if (session.scratched[i]) {
                if (i < rewardSlotCount) {
                    String rewardKey = session.rewardResults.get(i);
                    CardData.RewardEntry entry = cardData.getRewardByKey(rewardKey);
                    double baseReward = (entry != null) ? entry.getReward() : 0;
                    Material displayMat = getRewardDisplayMaterial(rewardKey);

                    ItemStack displayItem = new ItemStack(displayMat);
                    ItemMeta meta = displayItem.getItemMeta();
                    double actualReward = baseReward * globalMult;
                    if (actualReward > 0) {
                        meta.displayName(Component.text("§e§l+" + formatMoney(actualReward) + " 金币"));
                    } else {
                        meta.displayName(Component.text("§c§l谢谢参与"));
                    }
                    displayItem.setItemMeta(meta);
                    inv.setItem(slot, displayItem);
                } else {
                    int multIndex = i - rewardSlotCount;
                    int multiplierValue = session.multiplierResults.get(multIndex);
                    Material displayMat = getMultiplierDisplayMaterial(multiplierValue);

                    ItemStack multItem = new ItemStack(displayMat);
                    ItemMeta meta = multItem.getItemMeta();
                    meta.displayName(Component.text("§e§l×" + multiplierValue));
                    multItem.setItemMeta(meta);
                    inv.setItem(slot, multItem);
                }
            } else {
                ItemStack bedrock = new ItemStack(Material.BEDROCK);
                ItemMeta bMeta = bedrock.getItemMeta();
                bMeta.displayName(Component.text("§f§l?"));
                bedrock.setItemMeta(bMeta);
                inv.setItem(slot, bedrock);
            }
        }

        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    // ========== 背景填充 ==========

    private void fillBackground(Inventory inv, CardData cardData) {
        int size = cardData.getUiSize();
        Material bgMat = cardData.getBackgroundMaterial();
        Material highlightMat = cardData.getHighlightMaterial();

        ItemStack bg = new ItemStack(bgMat);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.text(" "));
        bg.setItemMeta(bgMeta);

        for (int i = 0; i < size; i++) {
            inv.setItem(i, bg);
        }

        if (highlightMat != null) {
            Set<Integer> highlightSlots = cardData.getHighlightSlots();
            ItemStack highlight = new ItemStack(highlightMat);
            ItemMeta hlMeta = highlight.getItemMeta();
            hlMeta.displayName(Component.text(" "));
            highlight.setItemMeta(hlMeta);

            for (int slot : highlightSlots) {
                boolean isButton = false;
                for (int bs : cardData.getSlotPositions()) {
                    if (bs == slot) { isButton = true; break; }
                }
                if (!isButton && slot >= 0 && slot < size) {
                    inv.setItem(slot, highlight);
                }
            }
        }
    }

    // ========== 音效工具方法 ==========

    /**
     * 根据音效名称播放音效，如果名称无效则静默忽略
     */
    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName);
            player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            // 音效名称无效，跳过
        }
    }

    // ========== 随机选择 ==========

    private String getRandomReward(CardData cardData, Random random) {
        List<CardData.RewardEntry> rewards = cardData.getRewards();
        int totalWeight = 0;
        for (CardData.RewardEntry entry : rewards) {
            totalWeight += entry.getWeight();
        }
        int rand = random.nextInt(totalWeight);
        for (CardData.RewardEntry entry : rewards) {
            rand -= entry.getWeight();
            if (rand < 0) return entry.getKey();
        }
        return rewards.isEmpty() ? "EMPTY" : rewards.get(0).getKey();
    }

    private int getRandomMultiplier(CardData cardData, Random random) {
        List<CardData.MultiplierEntry> mults = cardData.getMultipliers();
        if (mults.isEmpty()) return 1;
        double totalWeight = 0;
        for (CardData.MultiplierEntry entry : mults) {
            totalWeight += entry.getWeight();
        }
        double rand = random.nextDouble() * totalWeight;
        for (CardData.MultiplierEntry entry : mults) {
            rand -= entry.getWeight();
            if (rand < 0) return entry.getValue();
        }
        return 1;
    }

    // ========== 物品映射 ==========

    private Material getRewardDisplayMaterial(String rewardKey) {
        return REWARD_MATERIAL_MAP.getOrDefault(rewardKey, Material.BARRIER);
    }

    private Material getMultiplierDisplayMaterial(int multiplierValue) {
        return MULTIPLIER_MATERIAL_MAP.getOrDefault(multiplierValue, Material.QUARTZ);
    }

    // ========== 点击刮卡 ==========

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        CardSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        ItemStack current = event.getCurrentItem();
        if (current == null) return;
        event.setCancelled(true);

        if (session.completed) return;
        if (current.getType() != Material.BEDROCK) return;

        int slot = event.getSlot();
        int[] buttonSlots = session.cardData.getSlotPositions();
        int index = -1;
        for (int i = 0; i < buttonSlots.length; i++) {
            if (buttonSlots[i] == slot) { index = i; break; }
        }
        if (index == -1) return;
        if (session.scratched[index]) return;

        session.scratched[index] = true;
        session.remaining--;

        CardData cardData = session.cardData;
        CardData.SoundConfig sounds = cardData.getSounds();
        int rewardSlotCount = cardData.getRewardSlots();
        double globalMult = plugin.getMultiplier();

        if (index < rewardSlotCount) {
            // ===== 奖励槽 =====
            String rewardKey = session.rewardResults.get(index);
            CardData.RewardEntry entry = cardData.getRewardByKey(rewardKey);
            double baseReward = (entry != null) ? entry.getReward() : 0;
            Material displayMat = getRewardDisplayMaterial(rewardKey);

            ItemStack displayItem = new ItemStack(displayMat);
            ItemMeta meta = displayItem.getItemMeta();
            double actualReward = baseReward * globalMult;
            if (actualReward > 0) {
                meta.displayName(Component.text("§e§l+" + formatMoney(actualReward) + " 金币"));
                playSound(player, sounds.getReward(), 1.0f, 1.0f);
            } else {
                meta.displayName(Component.text("§c§l谢谢参与"));
                playSound(player, sounds.getEmpty(), 1.0f, 1.0f);
            }
            displayItem.setItemMeta(meta);
            event.getInventory().setItem(slot, displayItem);
        } else {
            // ===== 倍率槽 =====
            int multIndex = index - rewardSlotCount;
            int multiplierValue = session.multiplierResults.get(multIndex);
            Material displayMat = getMultiplierDisplayMaterial(multiplierValue);

            ItemStack multItem = new ItemStack(displayMat);
            ItemMeta meta = multItem.getItemMeta();
            meta.displayName(Component.text("§e§l×" + multiplierValue));
            multItem.setItemMeta(meta);
            event.getInventory().setItem(slot, multItem);

            // 倍率音效：根据配置的音效名称和音高范围计算
            float pitchMin = sounds.getMultiplierPitchMin();
            float pitchMax = sounds.getMultiplierPitchMax();
            int maxMult = 5;
            float pitch = pitchMin + (float)(multiplierValue - 1) / (maxMult - 1) * (pitchMax - pitchMin);
            if (pitch > 2.0f) pitch = 2.0f;
            if (pitch < 0.5f) pitch = 0.5f;
            playSound(player, sounds.getMultiplierSound(), 1.0f, pitch);
        }

        // 刮卡音效
        playSound(player, sounds.getScratch(), 0.5f, 1.0f);

        // ---- 全部刮完 ----
        if (session.remaining == 0) {
            double totalFinal;

            if (cardData.isBonusEnabled()) {
                totalFinal = calculateBonusTotal(session.rewardResults, cardData);
            } else {
                totalFinal = 0;
                for (String key : session.rewardResults) {
                    CardData.RewardEntry entry = cardData.getRewardByKey(key);
                    if (entry != null) totalFinal += entry.getReward();
                }
            }

            totalFinal *= globalMult;

            double multProduct = 1;
            for (int mv : session.multiplierResults) {
                multProduct *= mv;
            }
            totalFinal *= multProduct;

            ScratchPlugin.getEconomy().depositPlayer(player, totalFinal);
            player.sendMessage("§a你获得了 §e" + formatMoney(totalFinal) + " 金币！");

            // 全服公告
            CardType type = CardType.valueOf(cardData.getName().toUpperCase());
            if (type == CardType.GOLD || type == CardType.DIAMOND) {
                double actualPrice = cardData.getPrice() * globalMult;
                if (totalFinal > 2 * actualPrice) {
                    String cardDisplayName = (type == CardType.GOLD) ? "金刮刮卡" : "钻石刮刮卡";
                    String message = "<gradient:#FF0000:#FF8C00:#FFFF00:#00FF00:#00BFFF:#8A2BE2>" +
                            "[ScratchCardFumino]恭喜" + player.getName() + "在" + cardDisplayName + "中赢得了" + formatMoney(totalFinal) + "金币!</gradient>";
                    Bukkit.getServer().broadcast(MiniMessage.miniMessage().deserialize(message));
                }
            }

            session.completed = true;

            // 完成音效
            playSound(player, sounds.getComplete(), 1.0f, 1.0f);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) player.closeInventory();
            }, 20L);
        }
    }

    // ========== 加成计算 ==========

    private double calculateBonusTotal(List<String> results, CardData cardData) {
        Map<String, Integer> countMap = new HashMap<>();
        Map<String, Double> rewardMap = new HashMap<>();

        for (String key : results) {
            countMap.put(key, countMap.getOrDefault(key, 0) + 1);
            if (!rewardMap.containsKey(key)) {
                CardData.RewardEntry entry = cardData.getRewardByKey(key);
                rewardMap.put(key, (entry != null) ? entry.getReward() : 0);
            }
        }

        double total = 0;
        for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue();
            double base = rewardMap.get(key);
            if (base > 0 && count > 0) {
                total += (2 * count - 1) * base;
            }
        }
        return total;
    }

    // ========== 关闭界面 ==========

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        CardSession session = sessions.remove(player.getUniqueId());
        if (session != null && !session.completed) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) reopenCard(player, session);
            });
        }
    }

    // ========== 工具方法 ==========

    private String formatMoney(double amount) {
        if (amount == (long) amount) return String.valueOf((long) amount);
        return String.format("%.1f", amount);
    }

    // ========== 会话内部类 ==========

    private static class CardSession {
        CardData cardData;
        int slotCount;
        List<String> rewardResults = new ArrayList<>();
        List<Integer> multiplierResults = new ArrayList<>();
        boolean[] scratched;
        int remaining;
        boolean completed;

        CardSession(CardData cardData, int slotCount) {
            this.cardData = cardData;
            this.slotCount = slotCount;
            this.scratched = new boolean[slotCount];
            this.remaining = slotCount;
        }
    }
}
