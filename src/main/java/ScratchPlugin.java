package com.example.scratch;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ScratchPlugin extends JavaPlugin {

    private static Economy econ;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().severe("未找到 Vault 经济插件，插件将禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new TicketListener(this), this);
        getCommand("getcard").setExecutor(new AdminCommand(this));
        getCommand("buycard").setExecutor(new BuyCardCommand(this));
        getLogger().info("刮刮卡插件已启用！");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public double getMultiplier() {
        double m = getConfig().getDouble("multiplier", 500.0);
        if (m < 1) m = 1;
        return m;
    }
}
