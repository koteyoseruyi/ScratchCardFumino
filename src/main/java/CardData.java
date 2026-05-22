package com.example.scratch;

import org.bukkit.Material;

import java.util.*;

public class CardData {

    private final String name;
    private final String display;
    private final List<String> lore;
    private final int slotCount;
    private final double price;
    private final boolean bonusEnabled;
    private final int rewardSlots;
    private final int multiplierSlots;
    private final List<RewardEntry> rewards;
    private final List<MultiplierEntry> multipliers;

    // 界面配置
    private final int uiSize;
    private final String uiTitle;
    private final Material backgroundMaterial;
    private final int[] slotPositions;
    private final Material highlightMaterial;

    // 音效配置
    private final SoundConfig sounds;

    public CardData(String name, String display, List<String> lore, int slotCount,
                    double price, boolean bonusEnabled, int rewardSlots, int multiplierSlots,
                    List<RewardEntry> rewards, List<MultiplierEntry> multipliers,
                    int uiSize, String uiTitle, Material backgroundMaterial,
                    int[] slotPositions, Material highlightMaterial,
                    SoundConfig sounds) {
        this.name = name;
        this.display = display;
        this.lore = lore;
        this.slotCount = slotCount;
        this.price = price;
        this.bonusEnabled = bonusEnabled;
        this.rewardSlots = rewardSlots;
        this.multiplierSlots = multiplierSlots;
        this.rewards = rewards;
        this.multipliers = multipliers;
        this.uiSize = uiSize;
        this.uiTitle = uiTitle;
        this.backgroundMaterial = backgroundMaterial;
        this.slotPositions = slotPositions;
        this.highlightMaterial = highlightMaterial;
        this.sounds = sounds;
    }

    // ===== 基础信息 =====

    public String getName() { return name; }
    public String getDisplay() { return display; }
    public List<String> getLore() { return lore; }
    public int getSlotCount() { return slotCount; }
    public double getPrice() { return price; }
    public boolean isBonusEnabled() { return bonusEnabled; }
    public int getRewardSlots() { return rewardSlots; }
    public int getMultiplierSlots() { return multiplierSlots; }
    public List<RewardEntry> getRewards() { return rewards; }
    public List<MultiplierEntry> getMultipliers() { return multipliers; }

    // ===== 界面配置 =====

    public int getUiSize() { return uiSize; }
    public String getUiTitle() { return uiTitle; }
    public Material getBackgroundMaterial() { return backgroundMaterial; }
    public int[] getSlotPositions() { return slotPositions; }
    public Material getHighlightMaterial() { return highlightMaterial; }

    public int[] getMultiplierSlotPositions() {
        if (multiplierSlots <= 0) return new int[0];
        int[] multPos = new int[multiplierSlots];
        System.arraycopy(slotPositions, rewardSlots, multPos, 0, multiplierSlots);
        return multPos;
    }

    public Set<Integer> getHighlightSlots() {
        Set<Integer> highlightSet = new HashSet<>();
        int[] multPos = getMultiplierSlotPositions();
        int cols = 9;
        for (int ms : multPos) {
            int row = ms / cols;
            int col = ms % cols;
            if (row > 0) highlightSet.add(ms - cols);
            if (row < (uiSize / cols) - 1) highlightSet.add(ms + cols);
            if (col > 0) highlightSet.add(ms - 1);
            if (col < cols - 1) highlightSet.add(ms + 1);
        }
        return highlightSet;
    }

    // ===== 音效配置 =====

    public SoundConfig getSounds() { return sounds; }

    // ===== 奖励/倍率查找 =====

    public RewardEntry getRewardByKey(String key) {
        for (RewardEntry entry : rewards) {
            if (entry.getKey().equals(key)) return entry;
        }
        return null;
    }

    // ========== 内部类 ==========

    public static class RewardEntry {
        private final String key;
        private final int weight;
        private final double reward;

        public RewardEntry(String key, int weight, double reward) {
            this.key = key;
            this.weight = weight;
            this.reward = reward;
        }

        public String getKey() { return key; }
        public int getWeight() { return weight; }
        public double getReward() { return reward; }
    }

    public static class MultiplierEntry {
        private final int value;
        private final double weight;

        public MultiplierEntry(int value, double weight) {
            this.value = value;
            this.weight = weight;
        }

        public int getValue() { return value; }
        public double getWeight() { return weight; }
    }

    /**
     * 音效配置
     */
    public static class SoundConfig {
        private final String open;
        private final String reward;
        private final String empty;
        private final String scratch;
        private final String complete;
        private final String multiplierSound;
        private final float multiplierPitchMin;
        private final float multiplierPitchMax;

        public SoundConfig(String open, String reward, String empty,
                           String scratch, String complete,
                           String multiplierSound,
                           float multiplierPitchMin, float multiplierPitchMax) {
            this.open = open;
            this.reward = reward;
            this.empty = empty;
            this.scratch = scratch;
            this.complete = complete;
            this.multiplierSound = multiplierSound;
            this.multiplierPitchMin = multiplierPitchMin;
            this.multiplierPitchMax = multiplierPitchMax;
        }

        public String getOpen() { return open; }
        public String getReward() { return reward; }
        public String getEmpty() { return empty; }
        public String getScratch() { return scratch; }
        public String getComplete() { return complete; }
        public String getMultiplierSound() { return multiplierSound; }
        public float getMultiplierPitchMin() { return multiplierPitchMin; }
        public float getMultiplierPitchMax() { return multiplierPitchMax; }
    }
}
