package com.example.scratch;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GamblerTopCommand implements CommandExecutor, TabCompleter {

    private final ScratchPlugin plugin;

    public GamblerTopCommand(ScratchPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        StatsManager stats = plugin.getStatsManager();

        String type = "earned"; // 默认按赚取金额
        if (args.length > 0) {
            String input = args[0].toLowerCase();
            if (input.equals("profit") || input.equals("profit-rate") || input.equals("利润率")) {
                type = "profit";
            } else if (!input.equals("earned") && !input.equals("earning") && !input.equals("赚取金额")) {
                sender.sendMessage("§c无效的分类！可用: earned (赚取金额), profit (利润率)");
                return true;
            }
        }

        int limit = 10;

        if (type.equals("earned")) {
            List<StatsManager.PlayerStats> top = stats.getEarningTop(limit);
            sender.sendMessage("§6§l===== §e刮刮卡排行榜 (赚取金额) §6§l=====");
            if (top.isEmpty()) {
                sender.sendMessage("§c暂无数据。");
                return true;
            }
            int rank = 1;
            for (StatsManager.PlayerStats ps : top) {
                sender.sendMessage("§e#" + rank + " §7" + ps.getName() + " §f- §a" + formatMoney(ps.getEarned()) + " 金币"
                        + " §7(花费: " + formatMoney(ps.getSpent()) + " 利润率: " + String.format("%.1f", ps.getProfitRate()) + "%)");
                rank++;
            }
        } else {
            List<StatsManager.PlayerStats> top = stats.getProfitTop(limit);
            sender.sendMessage("§6§l===== §e刮刮卡排行榜 (利润率) §6§l=====");
            if (top.isEmpty()) {
                sender.sendMessage("§c暂无数据。");
                return true;
            }
            int rank = 1;
            for (StatsManager.PlayerStats ps : top) {
                String sign = ps.getProfitRate() >= 0 ? "§a" : "§c";
                sender.sendMessage("§e#" + rank + " §7" + ps.getName() + " §f- " + sign + String.format("%.1f", ps.getProfitRate()) + "%"
                        + " §7(赚取: " + formatMoney(ps.getEarned()) + " 花费: " + formatMoney(ps.getSpent()) + ")");
                rank++;
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> options = new ArrayList<>();
            options.add("earned");
            options.add("profit");
            return options.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        return List.of();
    }

    private String formatMoney(double amount) {
        if (amount == (long) amount) return String.valueOf((long) amount);
        return String.format("%.1f", amount);
    }
}
