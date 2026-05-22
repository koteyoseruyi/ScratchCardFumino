package com.example.scratch;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class CardLoader {

    private final JavaPlugin plugin;
    private final Map<String, CardData> cards = new LinkedHashMap<>();

    public CardLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadCards() {
        cards.clear();

        File cardsDir = new File(plugin.getDataFolder(), "cards");
        if (!cardsDir.exists()) {
            cardsDir.mkdirs();
        }

        saveDefaultCard("bronze.yml");
        saveDefaultCard("gold.yml");
        saveDefaultCard("diamond.yml");
        saveDefaultCard("witch.yml");

        File[] files = cardsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                String name = file.getName().replace(".yml", "");
                CardData data = parseCard(name, config);
                if (data != null) {
                    cards.put(name, data);
                    plugin.getLogger().info("已加载刮刮卡: " + name + " (分类: " + data.getCategory() + ")");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("无法加载刮刮卡文件: " + file.getName() + " - " + e.getMessage());
            }
        }

        plugin.getLogger().info("共加载 " + cards.size() + " 张刮刮卡");
    }

    private void saveDefaultCard(String fileName) {
        File cardFile = new File(plugin.getDataFolder(), "cards/" + fileName);
        if (!cardFile.exists()) {
            InputStream in = plugin.getResource("cards/" + fileName);
            if (in != null) {
                try {
                    YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(in));
                    defaultConfig.save(cardFile);
                } catch (Exception e) {
                    plugin.getLogger().warning("无法保存默认刮刮卡: " + fileName);
                }
            } else {
                plugin.getLogger().warning("未找到默认刮刮卡资源: " + fileName);
            }
        }
    }

    private CardData parseCard(String name, YamlConfiguration config) {
        String display = config.getString("display");
        if (display == null) {
            plugin.getLogger().warning("刮刮卡 " + name + " 缺少 display 字段，跳过");
            return null;
        }

        String category = config.getString("category", "eco");

        List<String> lore = config.getStringList("lore");
        int slotCount = config.getInt("slot-count", 3);
        double price = config.getDouble("price", 1.0);
        boolean bonusEnabled = config.getBoolean("bonus-enabled", false);
        int rewardSlots = config.getInt("reward-slots", slotCount);
        int multiplierSlots = config.getInt("multiplier-slots", 0);

        // ===== 解析界面配置 =====
        ConfigurationSection uiSection = config.getConfigurationSection("ui");
        int uiSize = 27;
        String uiTitle = display;
        Material bgMaterial = Material.BLACK_STAINED_GLASS_PANE;
        int[] slotPositions = new int[rewardSlots + multiplierSlots];
        Material highlightMaterial = null;

        if (uiSection != null) {
            uiSize = uiSection.getInt("size", 27);
            uiTitle = uiSection.getString("title", display);

            String bgStr = uiSection.getString("background-material", "BLACK_STAINED_GLASS_PANE");
            try {
                bgMaterial = Material.valueOf(bgStr);
            } catch (IllegalArgumentException e) {
                bgMaterial = Material.BLACK_STAINED_GLASS_PANE;
            }

            List<Integer> slotPosList = uiSection.getIntegerList("slot-positions");
            if (slotPosList.isEmpty()) {
                for (int i = 0; i < slotPositions.length; i++) {
                    slotPositions[i] = 10 + i * 2;
                }
            } else {
                slotPositions = slotPosList.stream().mapToInt(Integer::intValue).toArray();
            }

            String hlStr = uiSection.getString("highlight-material");
            if (hlStr != null && !hlStr.isEmpty()) {
                try {
                    highlightMaterial = Material.valueOf(hlStr);
                } catch (IllegalArgumentException ignored) {}
            }
        } else {
            for (int i = 0; i < slotPositions.length; i++) {
                slotPositions[i] = 10 + i * 2;
            }
        }

        // ===== 解析音效配置 =====
        ConfigurationSection soundsSection = config.getConfigurationSection("sounds");
        CardData.SoundConfig sounds = parseSounds(soundsSection);

        // ===== 解析奖励 =====
        List<CardData.RewardEntry> rewards = new ArrayList<>();
        ConfigurationSection rewardsSection = config.getConfigurationSection("rewards");
        if (rewardsSection != null) {
            for (String key : rewardsSection.getKeys(false)) {
                int weight = rewardsSection.getInt(key + ".weight", 0);
                double reward = rewardsSection.getDouble(key + ".reward", 0);
                if (weight > 0) {
                    rewards.add(new CardData.RewardEntry(key, weight, reward));
                }
            }
        }

        // misc 类卡片允许没有奖励配置
        if (rewards.isEmpty() && !"misc".equalsIgnoreCase(category)) {
            plugin.getLogger().warning("刮刮卡 " + name + " 没有有效的奖励配置，跳过");
            return null;
        }

        // ===== 解析倍率 =====
        List<CardData.MultiplierEntry> multipliers = new ArrayList<>();
        ConfigurationSection multSection = config.getConfigurationSection("multipliers");
        if (multSection != null) {
            for (String key : multSection.getKeys(false)) {
                try {
                    int value = Integer.parseInt(key);
                    double weight = multSection.getDouble(key + ".weight", 0);
                    if (weight > 0) {
                        multipliers.add(new CardData.MultiplierEntry(value, weight));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        return new CardData(name, category, display, lore, slotCount, price, bonusEnabled,
                rewardSlots, multiplierSlots, rewards, multipliers,
                uiSize, uiTitle, bgMaterial, slotPositions, highlightMaterial,
                sounds);
    }

    private CardData.SoundConfig parseSounds(ConfigurationSection section) {
        String open = "BLOCK_ANVIL_LAND";
        String reward = "ENTITY_VILLAGER_CELEBRATE";
        String empty = "ENTITY_VILLAGER_HURT";
        String scratch = "BLOCK_GRINDSTONE_USE";
        String complete = "ENTITY_FIREWORK_ROCKET_LAUNCH";
        String multiplierSound = "BLOCK_NOTE_BLOCK_HARP";
        float multiplierPitchMin = 0.6f;
        float multiplierPitchMax = 2.0f;

        if (section != null) {
            open = section.getString("open", open);
            reward = section.getString("reward", reward);
            empty = section.getString("empty", empty);
            scratch = section.getString("scratch", scratch);
            complete = section.getString("complete", complete);
            multiplierSound = section.getString("multiplier-sound", multiplierSound);
            multiplierPitchMin = (float) section.getDouble("multiplier-pitch-min", multiplierPitchMin);
            multiplierPitchMax = (float) section.getDouble("multiplier-pitch-max", multiplierPitchMax);
        }

        return new CardData.SoundConfig(open, reward, empty, scratch, complete,
                multiplierSound, multiplierPitchMin, multiplierPitchMax);
    }

    // ===== 查询方法 =====

    public List<String> getCardNames() {
        return new ArrayList<>(cards.keySet());
    }

    public Map<String, CardData> getAllCards() {
        return Collections.unmodifiableMap(cards);
    }

    public CardData getCardData(String name) {
        return cards.get(name.toLowerCase());
    }

    public CardData getCardData(CardType type) {
        return cards.get(type.name().toLowerCase());
    }

    public int getCardCount() {
        return cards.size();
    }
}
