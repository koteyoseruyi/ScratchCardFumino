package com.example.scratch;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class StatsManager {

    private final JavaPlugin plugin;
    private final File statsFile;
    private YamlConfiguration statsConfig;

    public StatsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    public void load() {
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("无法创建 stats.yml: " + e.getMessage());
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    public void save() {
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存 stats.yml: " + e.getMessage());
        }
    }

    /**
     * 记录玩家购买花费
     */
    public void recordPurchase(UUID uuid, String playerName, double cost) {
        String path = "players." + uuid.toString() + ".";
        statsConfig.set(path + "name", playerName);
        statsConfig.set(path + "total-spent", getTotalSpent(uuid) + cost);
        save();
    }

    /**
     * 记录玩家获得奖金
     */
    public void recordEarning(UUID uuid, String playerName, double amount) {
        String path = "players." + uuid.toString() + ".";
        statsConfig.set(path + "name", playerName);
        statsConfig.set(path + "total-earned", getTotalEarned(uuid) + amount);
        save();
    }

    // ===== 查询方法 =====

    public double getTotalSpent(UUID uuid) {
        return statsConfig.getDouble("players." + uuid.toString() + ".total-spent", 0.0);
    }

    public double getTotalEarned(UUID uuid) {
        return statsConfig.getDouble("players." + uuid.toString() + ".total-earned", 0.0);
    }

    /**
     * 获取玩家的利润率（百分比）
     * 利润率 = (总赚取 - 总花费) / 总花费 × 100%
     */
    public double getProfitRate(UUID uuid) {
        double spent = getTotalSpent(uuid);
        if (spent <= 0) return 0;
        double earned = getTotalEarned(uuid);
        return (earned - spent) / spent * 100.0;
    }

    /**
     * 获取所有有数据的玩家UUID列表
     */
    public List<UUID> getAllPlayerUUIDs() {
        List<UUID> uuids = new ArrayList<>();
        ConfigurationSection section = statsConfig.getConfigurationSection("players");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    uuids.add(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return uuids;
    }

    /**
     * 获取玩家名称（从缓存中读取）
     */
    public String getPlayerName(UUID uuid) {
        return statsConfig.getString("players." + uuid.toString() + ".name", "未知玩家");
    }

    /**
     * 按赚取金额排序的排行榜数据
     */
    public List<PlayerStats> getEarningTop(int limit) {
        List<PlayerStats> list = new ArrayList<>();
        for (UUID uuid : getAllPlayerUUIDs()) {
            double earned = getTotalEarned(uuid);
            if (earned > 0) {
                list.add(new PlayerStats(getPlayerName(uuid), uuid, earned, getTotalSpent(uuid), getProfitRate(uuid)));
            }
        }
        list.sort((a, b) -> Double.compare(b.earned, a.earned));
        if (limit > 0 && list.size() > limit) {
            list = list.subList(0, limit);
        }
        return list;
    }

    /**
     * 按利润率排序的排行榜数据
     */
    public List<PlayerStats> getProfitTop(int limit) {
        List<PlayerStats> list = new ArrayList<>();
        for (UUID uuid : getAllPlayerUUIDs()) {
            double spent = getTotalSpent(uuid);
            if (spent > 0) {
                list.add(new PlayerStats(getPlayerName(uuid), uuid, getTotalEarned(uuid), spent, getProfitRate(uuid)));
            }
        }
        list.sort((a, b) -> Double.compare(b.profitRate, a.profitRate));
        if (limit > 0 && list.size() > limit) {
            list = list.subList(0, limit);
        }
        return list;
    }

    // ===== 内部类 =====

    public static class PlayerStats {
        private final String name;
        private final UUID uuid;
        private final double earned;
        private final double spent;
        private final double profitRate;

        public PlayerStats(String name, UUID uuid, double earned, double spent, double profitRate) {
            this.name = name;
            this.uuid = uuid;
            this.earned = earned;
            this.spent = spent;
            this.profitRate = profitRate;
        }

        public String getName() { return name; }
        public UUID getUuid() { return uuid; }
        public double getEarned() { return earned; }
        public double getSpent() { return spent; }
        public double getProfitRate() { return profitRate; }
    }
}
