package com.example.scratch;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ScratchPlugin extends JavaPlugin {

    private static Economy econ;
    private CardLoader cardLoader;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 初始化卡片加载器
        cardLoader = new CardLoader(this);
        cardLoader.loadCards();

        if (!setupEconomy()) {
            getLogger().severe("未找到 Vault 经济插件，插件将禁用！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 注册监听器
        TicketListener ticketListener = new TicketListener(this);
        CardMenuListener cardMenuListener = new CardMenuListener(this);
        getServer().getPluginManager().registerEvents(ticketListener, this);
        getServer().getPluginManager().registerEvents(cardMenuListener, this);

        // 注册指令
        AdminCommand adminCommand = new AdminCommand(this);
        BuyCardCommand buyCardCommand = new BuyCardCommand(this);

        getCommand("getcard").setExecutor(adminCommand);
        getCommand("getcard").setTabCompleter(adminCommand);

        getCommand("buycard").setExecutor(buyCardCommand);
        getCommand("buycard").setTabCompleter(buyCardCommand);

        getCommand("scratchshop").setExecutor(cardMenuListener);

        getLogger().info("刮刮卡插件 v" + getDescription().getVersion() + " 已启用！");
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

    public CardLoader getCardLoader() {
        return cardLoader;
    }
}
