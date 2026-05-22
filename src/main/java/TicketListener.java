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

        CardType type = CardType.valueOf(typeStr);
        event.setCancelled(true);

        // 消耗手中一张卡
        item.setAmount(item.getAmount() - 1);
        event.getPlayer().getInventory().setItemInMainHand(item);

        openCard(event.getPlayer(), type);
    }

    private void openCard(Player player, CardType type) {
        String path = "cards." + type.name().toLowerCase() + ".";

        // 界面尺寸与标题
        int size;
        String titleRaw;
        switch (type) {
            case BRONZE -> {
                size = 27; // 9×3
                titleRaw = "<gradient:#CD7F32:#FFD700>铜刮刮卡</gradient>";
            }
            case GOLD -> {
                size = 27; // 9×3
                titleRaw = "<gradient:#FFD700:#FFA500>金刮刮卡</gradient>";
            }
            case DIAMOND -> {
                size = 54; // 9×6
                titleRaw = "<gradient:#00FFFF:#0080FF>钻石刮刮卡</gradient>";
            }
            default -> {
                size = 27;
                titleRaw = "刮刮卡";
            }
        }

        Inventory inv = Bukkit.createInventory(null, size,
                MiniMessage.miniMessage().deserialize(titleRaw));

        // ---- 填充背景 ----
        switch (type) {
            case BRONZE -> fillBronzeBackground(inv);
            case GOLD -> fillGoldBackground(inv);
            case DIAMOND -> fillDiamondBackground(inv);
        }

        // ---- 获取按钮槽位 ----
        int[] buttonSlots = getButtonSlots(type);
        int rewardSlotCount = getRewardSlotCount(type);
        int multiplierSlotCount = getMultiplierSlotCount(type);

        Random random = new Random();
        CardSession session = new CardSession(type, buttonSlots.length);
        session.rewardSlotCount = rewardSlotCount;
        session.multiplierSlotCount = multiplierSlotCount;
        session.bonusEnabled = plugin.getConfig().getBoolean(path + "bonus-enabled", false);

        // 生成奖励槽结果
        for (int i = 0; i < rewardSlotCount; i++) {
            int slot = buttonSlots[i];
            String rewardKey = getRandomReward(type, random);
            session.rewardResults.add(rewardKey);

            ItemStack bedrock = new ItemStack(Material.BEDROCK);
            ItemMeta bMeta = bedrock.getItemMeta();
            bMeta.displayName(Component.text("§f§l?"));
            bedrock.setItemMeta(bMeta);
            inv.setItem(slot, bedrock);
        }

        // 生成倍率槽结果（仅钻石卡）
        for (int i = rewardSlotCount; i < buttonSlots.length; i++) {
            int slot = buttonSlots[i];
            int multiplierValue = getRandomMultiplier(type, random);
            session.multiplierResults.add(multiplierValue);

            ItemStack bedrock = new ItemStack(Material.BEDROCK);
            ItemMeta bMeta = bedrock.getItemMeta();
            bMeta.displayName(Component.text("§f§l?"));
            bedrock.setItemMeta(bMeta);
            inv.setItem(slot, bedrock);
        }

        player.openInventory(inv);
        sessions.put(player.getUniqueId(), session);
        // 打开界面音效：铁砧
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f);
    }

    // ========== 背景填充 ==========

    private void fillBronzeBackground(Inventory inv) {
        ItemStack bg = new ItemStack(Material.BROWN_STAINED_GLASS_PANE);
        ItemMeta meta = bg.getItemMeta();
        meta.displayName(Component.text(" "));
        bg.setItemMeta(meta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }
    }

    private void fillGoldBackground(Inventory inv) {
        ItemStack bg = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
        ItemMeta meta = bg.getItemMeta();
        meta.displayName(Component.text(" "));
        bg.setItemMeta(meta);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, bg);
        }
    }

    private void fillDiamondBackground(Inventory inv) {
        // 先全部填浅蓝色
        ItemStack lightBlue = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta lbMeta = lightBlue.getItemMeta();
        lbMeta.displayName(Component.text(" "));
        lightBlue.setItemMeta(lbMeta);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, lightBlue);
        }

        // 倍率槽位置 38, 40, 42 的四周用蓝色玻璃板
        int[] multiplierSlots = {38, 40, 42};
        Set<Integer> blueSet = new HashSet<>();
        for (int ms : multiplierSlots) {
            int row = ms / 9;
            int col = ms % 9;
            // 上
            if (row > 0) blueSet.add(ms - 9);
            // 下
            if (row < 5) blueSet.add(ms + 9);
            // 左
            if (col > 0) blueSet.add(ms - 1);
            // 右
            if (col < 8) blueSet.add(ms + 1);
        }

        ItemStack blue = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta bMeta = blue.getItemMeta();
        bMeta.displayName(Component.text(" "));
        blue.setItemMeta(bMeta);

        for (int slot : blueSet) {
            inv.setItem(slot, blue);
        }
    }

    // ========== 槽位配置（已左移一格） ==========

    private int[] getButtonSlots(CardType type) {
        return switch (type) {
            case BRONZE -> new int[]{11, 13, 15};
            case GOLD -> new int[]{3, 5, 13, 21, 23};
            case DIAMOND -> new int[]{4, 11, 15, 21, 23, 38, 40, 42};
        };
    }

    private int getRewardSlotCount(CardType type) {
        return switch (type) {
            case BRONZE -> 3;
            case GOLD -> 5;
            case DIAMOND -> 5;
        };
    }

    private int getMultiplierSlotCount(CardType type) {
        return switch (type) {
            case BRONZE, GOLD -> 0;
            case DIAMOND -> 3;
        };
    }

    // ========== 随机选择 ==========

    private String getRandomReward(CardType type, Random random) {
        String path = "cards." + type.name().toLowerCase() + ".rewards";
        Set<String> keys = plugin.getConfig().getConfigurationSection(path).getKeys(false);
        int totalWeight = 0;
        for (String key : keys) {
            totalWeight += plugin.getConfig().getInt(path + "." + key + ".weight");
        }
        int rand = random.nextInt(totalWeight);
        for (String key : keys) {
            int weight = plugin.getConfig().getInt(path + "." + key + ".weight");
            rand -= weight;
            if (rand < 0) {
                return key;
            }
        }
        List<String> list = new ArrayList<>(keys);
        return list.isEmpty() ? "EMPTY" : list.get(0);
    }

    private int getRandomMultiplier(CardType type, Random random) {
        String path = "cards." + type.name().toLowerCase() + ".multipliers";
        Set<String> keys = plugin.getConfig().getConfigurationSection(path).getKeys(false);
        double totalWeight = 0;
        for (String key : keys) {
            totalWeight += plugin.getConfig().getDouble(path + "." + key + ".weight");
        }
        double rand = random.nextDouble() * totalWeight;
        for (String key : keys) {
            double weight = plugin.getConfig().getDouble(path + "." + key + ".weight");
            rand -= weight;
            if (rand < 0) {
                return Integer.parseInt(key);
            }
        }
        return 1;
    }

    // ========== 根据奖励类型获取显示物品 ==========

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

        // 已完成的界面只阻止点击但不报错
        if (session.completed) return;

        // 只允许点击基岩按钮
        if (current.getType() != Material.BEDROCK) return;

        int slot = event.getSlot();
        int[] buttonSlots = getButtonSlots(session.type);
        int index = -1;
        for (int i = 0; i < buttonSlots.length; i++) {
            if (buttonSlots[i] == slot) {
                index = i;
                break;
            }
        }
        if (index == -1) return;

        // 防止重复刮
        if (session.scratched[index]) return;
        session.scratched[index] = true;
        session.remaining--;

        String typePath = "cards." + session.type.name().toLowerCase() + ".";
        double globalMult = plugin.getMultiplier();

        if (index < session.rewardSlotCount) {
            // ===== 奖励槽 =====
            String rewardKey = session.rewardResults.get(index);
            double baseReward = plugin.getConfig().getDouble(typePath + "rewards." + rewardKey + ".reward");
            Material displayMat = getRewardDisplayMaterial(rewardKey);

            ItemStack displayItem = new ItemStack(displayMat);
            ItemMeta meta = displayItem.getItemMeta();

            double actualReward = baseReward * globalMult;
            if (actualReward > 0) {
                meta.displayName(Component.text("§e§l+" + formatMoney(actualReward) + " 金币"));
                // 刮到奖 → 村民开心音效
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1.0f, 1.0f);
            } else {
                meta.displayName(Component.text("§c§l谢谢参与"));
                // 刮到空 → 村民受击音效
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VILLAGER_HURT, 1.0f, 1.0f);
            }
            displayItem.setItemMeta(meta);
            event.getInventory().setItem(slot, displayItem);

        } else {
            // ===== 倍率槽（仅钻石卡） =====
            int multIndex = index - session.rewardSlotCount;
            int multiplierValue = session.multiplierResults.get(multIndex);
            Material displayMat = getMultiplierDisplayMaterial(multiplierValue);

            ItemStack multItem = new ItemStack(displayMat);
            ItemMeta meta = multItem.getItemMeta();
            meta.displayName(Component.text("§e§l×" + multiplierValue));
            multItem.setItemMeta(meta);
            event.getInventory().setItem(slot, multItem);

            // 音符盒不同音高
            float pitch = 0.6f + (multiplierValue - 1) * 0.25f;
            if (pitch > 2.0f) pitch = 2.0f;
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1.0f, pitch);
        }

        // 刮卡音效：砂轮
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.5f, 1.0f);

        // ---- 全部刮完 ----
        if (session.remaining == 0) {
            double totalFinal;
            String rewardsPath = typePath + "rewards.";

            if (session.bonusEnabled) {
                // 有加成：计算同类型加成
                totalFinal = calculateBonusTotal(session.rewardResults, rewardsPath);
            } else {
                // 无加成：简单求和
                totalFinal = 0;
                for (String key : session.rewardResults) {
                    totalFinal += plugin.getConfig().getDouble(rewardsPath + key + ".reward");
                }
            }

            // 乘以全局倍率
            totalFinal *= globalMult;

            // 乘以倍率乘积（仅钻石卡）
            double multProduct = 1;
            for (int mv : session.multiplierResults) {
                multProduct *= mv;
            }
            totalFinal *= multProduct;

            // 发放奖金
            ScratchPlugin.getEconomy().depositPlayer(player, totalFinal);
            player.sendMessage("§a你获得了 §e" + formatMoney(totalFinal) + " 金币！");

            // ===== 全服公告逻辑：仅金卡和钻石卡 =====
            CardType cardType = session.type;
            if (cardType == CardType.GOLD || cardType == CardType.DIAMOND) {
                // 获取定价 = price * multiplier
                double price = plugin.getConfig().getDouble(typePath + "price", 1.0);
                double actualPrice = price * globalMult;

                // 条件：奖励 - 定价 > 定价 × 100%  →  totalFinal > 2 * actualPrice
                if (totalFinal > 2 * actualPrice) {
                    // 构建彩虹渐变公告
                    // 格式：[ScratchCardFumino]恭喜[玩家名]在[刮刮卡类型]中赢得了[金额]!
                    String cardDisplayName = switch (cardType) {
                        case GOLD -> "金刮刮卡";
                        case DIAMOND -> "钻石刮刮卡";
                        default -> "刮刮卡";
                    };

                    // 使用红→橙→黄→绿→蓝→紫的彩虹渐变
                    String message = "<gradient:#FF0000:#FF8C00:#FFFF00:#00FF00:#00BFFF:#8A2BE2>" +
                            "[ScratchCardFumino]恭喜" + player.getName() + "在" + cardDisplayName + "中赢得了" + formatMoney(totalFinal) + "金币!</gradient>";

                    Component broadcastComponent = MiniMessage.miniMessage().deserialize(message);
                    Bukkit.getServer().broadcast(broadcastComponent);
                }
            }

            // 标记已完成
            session.completed = true;

            // 播放烟花音效
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);

            // 1秒后自动关闭界面
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.closeInventory();
                }
            }, 20L);
        }
    }

    // ========== 加成计算 ==========

    private double calculateBonusTotal(List<String> results, String rewardsPath) {
        Map<String, Integer> countMap = new HashMap<>();
        Map<String, Double> rewardMap = new HashMap<>();

        for (String key : results) {
            countMap.put(key, countMap.getOrDefault(key, 0) + 1);
            if (!rewardMap.containsKey(key)) {
                double base = plugin.getConfig().getDouble(rewardsPath + key + ".reward");
                rewardMap.put(key, base);
            }
        }

        double total = 0;
        for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
            String key = entry.getKey();
            int count = entry.getValue();
            double base = rewardMap.get(key);
            if (base > 0 && count > 0) {
                // 同类型加成：总奖金 = n*r + (n-1)*r = (2n-1)*r
                total += (2 * count - 1) * base;
            }
        }
        return total;
    }

    // ========== 关闭界面 ==========

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        CardSession session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null && !session.completed) {
            event.getPlayer().sendMessage("§c刮刮卡已作废！");
            ((Player) event.getPlayer()).getWorld().playSound(
                    event.getPlayer().getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f);
        }
    }

    // ========== 工具方法 ==========

    private String formatMoney(double amount) {
        if (amount == (long) amount) {
            return String.valueOf((long) amount);
        }
        return String.format("%.1f", amount);
    }

    // ========== 会话内部类 ==========

    private static class CardSession {
        CardType type;
        int slotCount;
        List<String> rewardResults = new ArrayList<>();
        List<Integer> multiplierResults = new ArrayList<>();
        boolean[] scratched;
        int remaining;
        boolean completed;
        boolean bonusEnabled;
        int rewardSlotCount;
        int multiplierSlotCount;

        CardSession(CardType type, int slotCount) {
            this.type = type;
            this.slotCount = slotCount;
            this.scratched = new boolean[slotCount];
            this.remaining = slotCount;
        }
    }
}
