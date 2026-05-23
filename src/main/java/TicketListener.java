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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class TicketListener implements Listener {

    private final ScratchPlugin plugin;
    private final NamespacedKey cardTypeKey;
    private final Map<UUID, CardSession> sessions = new HashMap<>();
    private final Map<UUID, Long> witchCooldowns = new HashMap<>();

    private static final Map<String, Material> REWARD_MATERIAL_MAP = new HashMap<>();
    static {
        REWARD_MATERIAL_MAP.put("EMPTY", Material.BARRIER);
        REWARD_MATERIAL_MAP.put("SMALL", Material.COPPER_INGOT);
        REWARD_MATERIAL_MAP.put("MEDIUM", Material.GOLD_INGOT);
        REWARD_MATERIAL_MAP.put("LARGE", Material.DIAMOND);
        REWARD_MATERIAL_MAP.put("HUGE", Material.EMERALD);
    }

    private static final Map<Integer, Material> MULTIPLIER_MATERIAL_MAP = new HashMap<>();
    static {
        MULTIPLIER_MATERIAL_MAP.put(1, Material.QUARTZ);
        MULTIPLIER_MATERIAL_MAP.put(2, Material.AMETHYST_SHARD);
        MULTIPLIER_MATERIAL_MAP.put(3, Material.NETHERITE_SCRAP);
        MULTIPLIER_MATERIAL_MAP.put(4, Material.NETHER_STAR);
        MULTIPLIER_MATERIAL_MAP.put(5, Material.DRAGON_EGG);
    }

    private static final List<PotionEffectType> WITCH_PRESET1_EFFECTS = new ArrayList<>();
    static {
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.SPEED);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.HASTE);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.RESISTANCE);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.JUMP_BOOST);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.STRENGTH);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.REGENERATION);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.HEALTH_BOOST);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.WATER_BREATHING);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.INVISIBILITY);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.NIGHT_VISION);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.NAUSEA);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.POISON);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.WITHER);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.HUNGER);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.SLOWNESS);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.WEAKNESS);
        WITCH_PRESET1_EFFECTS.add(PotionEffectType.MINING_FATIGUE);
    }

    private static final List<PotionEffectType> ALL_POSITIVE_EFFECTS = new ArrayList<>();
    static {
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.SPEED);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.HASTE);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.RESISTANCE);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.JUMP_BOOST);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.STRENGTH);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.REGENERATION);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.HEALTH_BOOST);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.WATER_BREATHING);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.INVISIBILITY);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.NIGHT_VISION);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.DOLPHINS_GRACE);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.CONDUIT_POWER);
        ALL_POSITIVE_EFFECTS.add(PotionEffectType.LUCK);
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

        // === 女巫卡冷却检查 ===
        if (type == CardType.WITCH) {
            UUID uuid = event.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            if (witchCooldowns.containsKey(uuid)) {
                long cooldownEnd = witchCooldowns.get(uuid);
                if (now < cooldownEnd) {
                    long remainingSeconds = (cooldownEnd - now + 999) / 1000;
                    event.getPlayer().sendActionBar(Component.text("§c女巫的刮刮卡冷却中: §e" + remainingSeconds + " 秒"));
                    event.setCancelled(true);
                    return;
                }
            }
        }

        CardData cardData = plugin.getCardLoader().getCardData(type);
        if (cardData == null) {
            event.getPlayer().sendMessage("§c该刮刮卡配置已失效！");
            return;
        }

        event.setCancelled(true);

        item.setAmount(item.getAmount() - 1);
        event.getPlayer().getInventory().setItemInMainHand(item);

        if (cardData.isMisc()) {
            openMiscCard(event.getPlayer(), cardData);
        } else {
            openEcoCard(event.getPlayer(), cardData);
        }
    }

    // ================================================================
    //  ECO 类卡片
    // ================================================================

    private void openEcoCard(Player player, CardData cardData) {
        int size = cardData.getUiSize();
        String titleRaw = cardData.getUiTitle();
        int[] buttonSlots = cardData.getSlotPositions();
        int rewardSlotCount = cardData.getRewardSlots();

        Inventory inv = Bukkit.createInventory(null, size,
                MiniMessage.miniMessage().deserialize(titleRaw));

        fillEcoBackground(inv, cardData);

        Random random = new Random();
        CardSession session = new CardSession(cardData, buttonSlots.length);

        for (int i = 0; i < rewardSlotCount; i++) {
            session.rewardResults.add(getRandomReward(cardData, random));
        }
        for (int i = rewardSlotCount; i < buttonSlots.length; i++) {
            session.multiplierResults.add(getRandomMultiplier(cardData, random));
        }

        for (int i = 0; i < buttonSlots.length; i++) {
            inv.setItem(buttonSlots[i], createBedrockButton());
        }

        player.openInventory(inv);
        sessions.put(player.getUniqueId(), session);
        playSound(player, cardData.getSounds().getOpen(), 0.5f, 1.0f);
    }

    private void fillEcoBackground(Inventory inv, CardData cardData) {
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

            Set<Integer> buttonSet = new HashSet<>();
            for (int bs : cardData.getSlotPositions()) buttonSet.add(bs);

            for (int slot : highlightSlots) {
                if (!buttonSet.contains(slot) && slot >= 0 && slot < size) {
                    inv.setItem(slot, highlight);
                }
            }
        }
    }

    // ================================================================
    //  MISC 类卡片（女巫卡）
    // ================================================================

    private void openMiscCard(Player player, CardData cardData) {
        int size = cardData.getUiSize();
        String titleRaw = cardData.getUiTitle();
        int[] buttonSlots = cardData.getSlotPositions();

        Inventory inv = Bukkit.createInventory(null, size,
                MiniMessage.miniMessage().deserialize(titleRaw));

        // 使用配置驱动的背景填充
        fillDecoratedBackground(inv, cardData);

        Random random = new Random();
        CardSession session = new CardSession(cardData, buttonSlots.length);

        double roll = random.nextDouble() * 100;
        int preset;
        if (roll < 1.0) {
            preset = 3;
        } else if (roll < 2.0) {
            preset = 2;
        } else {
            preset = 1;
        }
        session.witchPreset = preset;

        if (preset == 1) {
            session.witchBottleEffects = new PotionEffectType[3];
            for (int i = 0; i < 3; i++) {
                session.witchBottleEffects[i] = WITCH_PRESET1_EFFECTS.get(random.nextInt(WITCH_PRESET1_EFFECTS.size()));
            }
            int[] timeBases = {20, 30, 40, 50, 60, 64};
            session.witchTimeBase = timeBases[random.nextInt(timeBases.length)];
            double[] tMultipliers = {0.75, 1.0, 1.25};
            session.witchTimeMultiplier = tMultipliers[random.nextInt(tMultipliers.length)];
        }

        for (int slot : buttonSlots) {
            inv.setItem(slot, createBedrockButton());
        }

        player.openInventory(inv);
        sessions.put(player.getUniqueId(), session);
        playSound(player, cardData.getSounds().getOpen(), 0.5f, 1.0f);
    }

    /**
     * 通用装饰背景填充（从卡片配置读取 decoration 层）
     * 先填充背景材质，然后逐层覆盖装饰材质
     */
    private void fillDecoratedBackground(Inventory inv, CardData cardData) {
        int size = cardData.getUiSize();
        Material bgMat = cardData.getBackgroundMaterial();

        // 填充基础背景
        ItemStack bg = new ItemStack(bgMat);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.displayName(Component.text(" "));
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < size; i++) {
            inv.setItem(i, bg);
        }

        // 获取按钮槽位集合（防止装饰覆盖按钮）
        Set<Integer> buttonSet = new HashSet<>();
        for (int bs : cardData.getSlotPositions()) {
            buttonSet.add(bs);
        }

        // 逐层覆盖装饰材质
        List<CardData.DecorationLayer> decoration = cardData.getDecoration();
        if (decoration != null) {
            for (CardData.DecorationLayer layer : decoration) {
                ItemStack decorItem = new ItemStack(layer.getMaterial());
                ItemMeta decorMeta = decorItem.getItemMeta();
                decorMeta.displayName(Component.text(" "));
                decorItem.setItemMeta(decorMeta);

                for (int slot : layer.getSlots()) {
                    if (slot >= 0 && slot < size && !buttonSet.contains(slot)) {
                        inv.setItem(slot, decorItem);
                    }
                }
            }
        }
    }

    // ================================================================
    //  通用方法
    // ================================================================

    private ItemStack createBedrockButton() {
        ItemStack bedrock = new ItemStack(Material.BEDROCK);
        ItemMeta bMeta = bedrock.getItemMeta();
        bMeta.displayName(Component.text("§f§l?"));
        bedrock.setItemMeta(bMeta);
        return bedrock;
    }

    private void playSound(Player player, String soundName, float volume, float pitch) {
        try {
            NamespacedKey soundKey = NamespacedKey.fromString(soundName.toLowerCase().replace("_", "."));
            if (soundKey != null) {
                Sound sound = Registry.SOUND_EVENT.get(soundKey);
                if (sound != null) {
                    player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
                    return;
                }
            }
            Sound sound = Sound.valueOf(soundName);
            player.getWorld().playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {}
    }

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

    private Material getRewardDisplayMaterial(String rewardKey) {
        return REWARD_MATERIAL_MAP.getOrDefault(rewardKey, Material.BARRIER);
    }

    private Material getMultiplierDisplayMaterial(int multiplierValue) {
        return MULTIPLIER_MATERIAL_MAP.getOrDefault(multiplierValue, Material.QUARTZ);
    }

    // ================================================================
    //  点击事件
    // ================================================================

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

        if (session.cardData.isEco()) {
            handleEcoCardClick(player, session, index, slot);
        } else {
            handleMiscCardClick(player, session, index, slot);
        }
    }

    // ========== ECO 卡片点击 ==========

    private void handleEcoCardClick(Player player, CardSession session, int index, int slot) {
        CardData cardData = session.cardData;
        CardData.SoundConfig sounds = cardData.getSounds();
        int rewardSlotCount = cardData.getRewardSlots();
        double globalMult = plugin.getMultiplier();

        if (index < rewardSlotCount) {
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
            player.getOpenInventory().getTopInventory().setItem(slot, displayItem);
        } else {
            int multIndex = index - rewardSlotCount;
            int multiplierValue = session.multiplierResults.get(multIndex);
            Material displayMat = getMultiplierDisplayMaterial(multiplierValue);

            ItemStack multItem = new ItemStack(displayMat);
            ItemMeta meta = multItem.getItemMeta();
            meta.displayName(Component.text("§e§l×" + multiplierValue));
            multItem.setItemMeta(meta);
            player.getOpenInventory().getTopInventory().setItem(slot, multItem);

            float pitchMin = sounds.getMultiplierPitchMin();
            float pitchMax = sounds.getMultiplierPitchMax();
            float pitch = pitchMin + (float)(multiplierValue - 1) / 4.0f * (pitchMax - pitchMin);
            if (pitch > 2.0f) pitch = 2.0f;
            if (pitch < 0.5f) pitch = 0.5f;
            playSound(player, sounds.getMultiplierSound(), 1.0f, pitch);
        }

        playSound(player, sounds.getScratch(), 0.5f, 1.0f);

        if (session.remaining == 0) {
            completeEcoCard(player, session);
        }
    }

    private void completeEcoCard(Player player, CardSession session) {
        CardData cardData = session.cardData;
        double globalMult = plugin.getMultiplier();
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

        plugin.getStatsManager().recordEarning(player.getUniqueId(), player.getName(), totalFinal);

        CardType type = CardType.valueOf(cardData.getName().toUpperCase());
        if (type == CardType.GOLD || type == CardType.DIAMOND) {
            double actualPrice = cardData.getPrice() * globalMult;
            if (totalFinal > 3 * actualPrice) {
                String cardDisplayName = (type == CardType.GOLD) ? "金刮刮卡" : "钻石刮刮卡";
                String message = "<gradient:#FF0000:#FF8C00:#FFFF00:#00FF00:#00BFFF:#8A2BE2>" +
                        "[ScratchCardFumino]恭喜" + player.getName() + "在" + cardDisplayName + "中赢得了" + formatMoney(totalFinal) + "金币!</gradient>";
                Bukkit.getServer().broadcast(MiniMessage.miniMessage().deserialize(message));
            }
        }

        session.completed = true;
        playSound(player, cardData.getSounds().getComplete(), 1.0f, 1.0f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.closeInventory();
        }, 20L);
    }

    // ========== MISC 卡片（女巫卡）点击 ==========

    private void handleMiscCardClick(Player player, CardSession session, int index, int slot) {
        int preset = session.witchPreset;

        if (preset == 1) {
            if (index == 0 || index == 4) {
                ItemStack clock = new ItemStack(Material.CLOCK);
                ItemMeta meta = clock.getItemMeta();
                if (index == 0) {
                    meta.displayName(Component.text("§e§l" + session.witchTimeBase + "s"));
                } else {
                    meta.displayName(Component.text("§e§l×" + session.witchTimeMultiplier));
                }
                clock.setItemMeta(meta);
                player.getOpenInventory().getTopInventory().setItem(slot, clock);
                playSound(player, "BLOCK_RESPAWN_ANCHOR_CHARGE", 1.0f, 1.0f);
            } else {
                int bottleIndex = index - 1;
                PotionEffectType effectType = session.witchBottleEffects[bottleIndex];
                boolean isPositive = isPositiveEffect(effectType);
                Material mat = isPositive ? Material.POTION : Material.SPLASH_POTION;
                String effectName = getEffectDisplayName(effectType);

                ItemStack potion = new ItemStack(mat);
                ItemMeta meta = potion.getItemMeta();
                meta.displayName(Component.text((isPositive ? "§a" : "§c") + effectName));
                potion.setItemMeta(meta);
                player.getOpenInventory().getTopInventory().setItem(slot, potion);
                playSound(player, "BLOCK_BREWING_STAND_BREW", 1.0f, 1.0f);
            }
        } else if (preset == 2) {
            if (index == 0 || index == 4) {
                ItemStack barrier = new ItemStack(Material.BARRIER);
                ItemMeta meta = barrier.getItemMeta();
                meta.displayName(Component.text("§c§l致死毒药"));
                barrier.setItemMeta(meta);
                player.getOpenInventory().getTopInventory().setItem(slot, barrier);
            } else {
                ItemStack potion = new ItemStack(Material.SPLASH_POTION);
                ItemMeta meta = potion.getItemMeta();
                meta.displayName(Component.text("§c§l瞬间伤害"));
                potion.setItemMeta(meta);
                player.getOpenInventory().getTopInventory().setItem(slot, potion);
            }
            playSound(player, "ENTITY_ENDER_DRAGON_GROWL", 1.0f, 1.0f);
        } else if (preset == 3) {
            if (index == 0 || index == 4) {
                ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
                ItemMeta meta = totem.getItemMeta();
                meta.displayName(Component.text("§6§l稀世神药"));
                totem.setItemMeta(meta);
                player.getOpenInventory().getTopInventory().setItem(slot, totem);
            } else {
                ItemStack potion = new ItemStack(Material.POTION);
                ItemMeta meta = potion.getItemMeta();
                meta.displayName(Component.text("§6§l力量"));
                potion.setItemMeta(meta);
                player.getOpenInventory().getTopInventory().setItem(slot, potion);
            }
            playSound(player, "ENTITY_VILLAGER_CELEBRATE", 1.0f, 1.0f);
        }

        playSound(player, "BLOCK_GRINDSTONE_USE", 0.5f, 1.0f);

        if (session.remaining == 0) {
            completeWitchCard(player, session);
        }
    }

    private void completeWitchCard(Player player, CardSession session) {
        int preset = session.witchPreset;
        int cooldownSeconds = 0;

        if (preset == 1) {
            int baseTime = session.witchTimeBase;
            double multiplier = session.witchTimeMultiplier;
            int duration = (int) (baseTime * multiplier * 20);

            Map<PotionEffectType, Integer> effectCount = new HashMap<>();
            for (PotionEffectType effect : session.witchBottleEffects) {
                effectCount.put(effect, effectCount.getOrDefault(effect, 0) + 1);
            }

            for (Map.Entry<PotionEffectType, Integer> entry : effectCount.entrySet()) {
                PotionEffectType type = entry.getKey();
                int count = entry.getValue();
                int amplifier = count - 1;

                int actualDuration = duration;
                if (actualDuration < 20) actualDuration = 20;

                player.addPotionEffect(new PotionEffect(type, actualDuration, amplifier, false, true));
            }

            cooldownSeconds = (int) (baseTime * multiplier);

            player.sendMessage("§a你获得了药水效果！持续时间: §e" + (duration / 20) + " 秒");
            playSound(player, "ENTITY_GENERIC_DRINK", 1.0f, 1.0f);

        } else if (preset == 2) {
            player.setHealth(0);
            String message = "<gradient:#8B0000:#FF0000>[ScratchCardFumino]" + player.getName() + "在女巫的刮刮卡中刮到了致死毒药!</gradient>";
            Bukkit.getServer().broadcast(MiniMessage.miniMessage().deserialize(message));
            playSound(player, "ENTITY_ENDER_DRAGON_GROWL", 1.0f, 1.0f);

        } else if (preset == 3) {
            for (PotionEffectType effect : ALL_POSITIVE_EFFECTS) {
                player.addPotionEffect(new PotionEffect(effect, 300 * 20, 2, false, true));
            }
            String message = "<gradient:#C0C0C0:#FFD700>[ScratchCardFumino]" + player.getName() + "在女巫的刮刮卡中刮到了稀世神药!</gradient>";
            Bukkit.getServer().broadcast(MiniMessage.miniMessage().deserialize(message));
            player.sendMessage("§6§l你获得了所有正面效果 III (300秒)！");
            playSound(player, "UI_TOAST_CHALLENGE_COMPLETE", 1.0f, 1.0f);
            cooldownSeconds = 300;
        }

        if (cooldownSeconds > 0) {
            long cooldownEnd = System.currentTimeMillis() + (cooldownSeconds * 1000L);
            witchCooldowns.put(player.getUniqueId(), cooldownEnd);
            player.sendMessage("§7女巫的刮刮卡进入冷却: §e" + cooldownSeconds + " 秒");
        }

        session.completed = true;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.closeInventory();
        }, 40L);
    }

    // ================================================================
    //  药水效果工具
    // ================================================================

    private boolean isPositiveEffect(PotionEffectType type) {
        return type == PotionEffectType.SPEED ||
                type == PotionEffectType.HASTE ||
                type == PotionEffectType.RESISTANCE ||
                type == PotionEffectType.JUMP_BOOST ||
                type == PotionEffectType.STRENGTH ||
                type == PotionEffectType.REGENERATION ||
                type == PotionEffectType.HEALTH_BOOST ||
                type == PotionEffectType.WATER_BREATHING ||
                type == PotionEffectType.INVISIBILITY ||
                type == PotionEffectType.NIGHT_VISION ||
                type == PotionEffectType.DOLPHINS_GRACE ||
                type == PotionEffectType.CONDUIT_POWER ||
                type == PotionEffectType.LUCK;
    }

    private String getEffectDisplayName(PotionEffectType type) {
        if (type == PotionEffectType.SPEED) return "速度";
        if (type == PotionEffectType.HASTE) return "急迫";
        if (type == PotionEffectType.RESISTANCE) return "抗性提升";
        if (type == PotionEffectType.JUMP_BOOST) return "跳跃提升";
        if (type == PotionEffectType.STRENGTH) return "力量";
        if (type == PotionEffectType.REGENERATION) return "生命恢复";
        if (type == PotionEffectType.HEALTH_BOOST) return "生命提升";
        if (type == PotionEffectType.WATER_BREATHING) return "水下呼吸";
        if (type == PotionEffectType.INVISIBILITY) return "隐身";
        if (type == PotionEffectType.NIGHT_VISION) return "夜视";
        if (type == PotionEffectType.NAUSEA) return "反胃";
        if (type == PotionEffectType.POISON) return "中毒";
        if (type == PotionEffectType.WITHER) return "凋零";
        if (type == PotionEffectType.HUNGER) return "饥饿";
        if (type == PotionEffectType.SLOWNESS) return "缓慢";
        if (type == PotionEffectType.WEAKNESS) return "虚弱";
        if (type == PotionEffectType.MINING_FATIGUE) return "挖掘疲劳";
        if (type == PotionEffectType.DOLPHINS_GRACE) return "海豚的恩惠";
        if (type == PotionEffectType.CONDUIT_POWER) return "潮涌能量";
        if (type == PotionEffectType.LUCK) return "幸运";
        return type.getName();
    }

    // ================================================================
    //  加成计算（仅 ECO 卡使用）
    // ================================================================

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

    // ================================================================
    //  关闭界面（阻止关闭，重新打开）
    // ================================================================

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

    private void reopenCard(Player player, CardSession session) {
        CardData cardData = session.cardData;
        int size = cardData.getUiSize();
        String titleRaw = cardData.getUiTitle();
        int[] buttonSlots = cardData.getSlotPositions();

        Inventory inv = Bukkit.createInventory(null, size,
                MiniMessage.miniMessage().deserialize(titleRaw));

        if (cardData.isMisc()) {
            fillDecoratedBackground(inv, cardData);
            for (int i = 0; i < buttonSlots.length; i++) {
                int slot = buttonSlots[i];
                if (session.scratched[i]) {
                    displayMiscScratchedSlot(inv, session, i, slot);
                } else {
                    inv.setItem(slot, createBedrockButton());
                }
            }
        } else {
            fillEcoBackground(inv, cardData);
            int rewardSlotCount = cardData.getRewardSlots();
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
                    inv.setItem(slot, createBedrockButton());
                }
            }
        }

        sessions.put(player.getUniqueId(), session);
        player.openInventory(inv);
    }

    private void displayMiscScratchedSlot(Inventory inv, CardSession session, int index, int slot) {
        int preset = session.witchPreset;

        if (preset == 1) {
            if (index == 0) {
                ItemStack clock = new ItemStack(Material.CLOCK);
                ItemMeta meta = clock.getItemMeta();
                meta.displayName(Component.text("§e§l" + session.witchTimeBase + "s"));
                clock.setItemMeta(meta);
                inv.setItem(slot, clock);
            } else if (index == 4) {
                ItemStack clock = new ItemStack(Material.CLOCK);
                ItemMeta meta = clock.getItemMeta();
                meta.displayName(Component.text("§e§l×" + session.witchTimeMultiplier));
                clock.setItemMeta(meta);
                inv.setItem(slot, clock);
            } else {
                int bottleIndex = index - 1;
                PotionEffectType effectType = session.witchBottleEffects[bottleIndex];
                boolean isPositive = isPositiveEffect(effectType);
                Material mat = isPositive ? Material.POTION : Material.SPLASH_POTION;
                String effectName = getEffectDisplayName(effectType);
                ItemStack potion = new ItemStack(mat);
                ItemMeta meta = potion.getItemMeta();
                meta.displayName(Component.text((isPositive ? "§a" : "§c") + effectName));
                potion.setItemMeta(meta);
                inv.setItem(slot, potion);
            }
        } else if (preset == 2) {
            if (index == 0 || index == 4) {
                ItemStack barrier = new ItemStack(Material.BARRIER);
                ItemMeta meta = barrier.getItemMeta();
                meta.displayName(Component.text("§c§l致死毒药"));
                barrier.setItemMeta(meta);
                inv.setItem(slot, barrier);
            } else {
                ItemStack potion = new ItemStack(Material.SPLASH_POTION);
                ItemMeta meta = potion.getItemMeta();
                meta.displayName(Component.text("§c§l瞬间伤害"));
                potion.setItemMeta(meta);
                inv.setItem(slot, potion);
            }
        } else if (preset == 3) {
            if (index == 0 || index == 4) {
                ItemStack totem = new ItemStack(Material.TOTEM_OF_UNDYING);
                ItemMeta meta = totem.getItemMeta();
                meta.displayName(Component.text("§6§l稀世神药"));
                totem.setItemMeta(meta);
                inv.setItem(slot, totem);
            } else {
                ItemStack potion = new ItemStack(Material.POTION);
                ItemMeta meta = potion.getItemMeta();
                meta.displayName(Component.text("§6§l力量"));
                potion.setItemMeta(meta);
                inv.setItem(slot, potion);
            }
        }
    }

    // ================================================================
    //  工具方法
    // ================================================================

    private String formatMoney(double amount) {
        if (amount == (long) amount) return String.valueOf((long) amount);
        return String.format("%.1f", amount);
    }

    // ================================================================
    //  会话内部类
    // ================================================================

    private static class CardSession {
        CardData cardData;
        int slotCount;
        List<String> rewardResults = new ArrayList<>();
        List<Integer> multiplierResults = new ArrayList<>();
        boolean[] scratched;
        int remaining;
        boolean completed;

        int witchPreset;
        PotionEffectType[] witchBottleEffects;
        int witchTimeBase;
        double witchTimeMultiplier;

        CardSession(CardData cardData, int slotCount) {
            this.cardData = cardData;
            this.slotCount = slotCount;
            this.scratched = new boolean[slotCount];
            this.remaining = slotCount;
        }
    }
}
