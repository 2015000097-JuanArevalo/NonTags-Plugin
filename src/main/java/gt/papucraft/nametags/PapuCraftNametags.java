package gt.papucraft.nametags;

import me.clip.placeholderapi.PlaceholderAPI;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class PapuCraftNametags extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private File playersFile;
    private YamlConfiguration playersData;
    private BukkitTask refreshTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPlayersData();

        if (Bukkit.getPluginManager().getPlugin("TAB") == null) {
            getLogger().severe("TAB no está instalado. Desactivando PapuCraftNametags.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("PlaceholderAPI no está instalado. Desactivando PapuCraftNametags.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (TabAPI.getInstance().getNameTagManager() == null) {
            getLogger().severe("TAB tiene scoreboard-teams/nametags desactivado. Actívalo antes de usar este bridge.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Objects.requireNonNull(getCommand("nametag"), "Comando nametag no registrado")
                .setExecutor(this);
        Objects.requireNonNull(getCommand("nametag"))
                .setTabCompleter(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        startRefreshTask();
        getLogger().info("PapuCraftNametags habilitado.");
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        savePlayersData();
    }

    private void loadPlayersData() {
        playersFile = new File(getDataFolder(), "players.yml");
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("No se pudo crear la carpeta del plugin.");
        }
        playersData = YamlConfiguration.loadConfiguration(playersFile);
    }

    private void savePlayersData() {
        try {
            playersData.save(playersFile);
        } catch (IOException ex) {
            getLogger().severe("No se pudo guardar players.yml: " + ex.getMessage());
        }
    }

    private void startRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        long period = Math.max(20L, getConfig().getLong("refresh-ticks", 40L));
        refreshTask = Bukkit.getScheduler().runTaskTimer(this, this::refreshAll, 20L, period);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // TAB carga jugadores de forma asíncrona. Un pequeño retraso permite que exista TabPlayer.
        Bukkit.getScheduler().runTaskLater(this, this::refreshAll, 30L);
    }

    private void refreshAll() {
        NameTagManager manager = TabAPI.getInstance().getNameTagManager();
        if (manager == null) {
            return;
        }

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) {
            return;
        }

        Map<UUID, String> clans = new HashMap<>();
        Map<UUID, TabPlayer> tabPlayers = new HashMap<>();

        for (Player player : onlinePlayers) {
            clans.put(player.getUniqueId(), clanOf(player));
            TabPlayer tabPlayer = TabAPI.getInstance().getPlayer(player.getUniqueId());
            if (tabPlayer != null) {
                tabPlayers.put(player.getUniqueId(), tabPlayer);
            }
        }

        for (Player target : onlinePlayers) {
            TabPlayer targetTab = tabPlayers.get(target.getUniqueId());
            if (targetTab == null) {
                continue;
            }

            String targetClan = clans.getOrDefault(target.getUniqueId(), "");
            boolean publicNametag = isPublic(target.getUniqueId());

            for (Player viewer : onlinePlayers) {
                if (target.getUniqueId().equals(viewer.getUniqueId())) {
                    continue;
                }

                TabPlayer viewerTab = tabPlayers.get(viewer.getUniqueId());
                if (viewerTab == null) {
                    continue;
                }

                String viewerClan = clans.getOrDefault(viewer.getUniqueId(), "");
                boolean sameClan = !targetClan.isBlank()
                        && !viewerClan.isBlank()
                        && targetClan.equalsIgnoreCase(viewerClan);

                // Regla de PapuCraft Gamma:
                // 1) Mismo clan -> SIEMPRE visible.
                // 2) Otro clan / sin clan -> decide el jugador observado con /nametag on|off.
                boolean shouldShow = sameClan || publicNametag;
                boolean currentlyHidden = manager.hasHiddenNameTag(targetTab, viewerTab);

                if (shouldShow && currentlyHidden) {
                    manager.showNameTag(targetTab, viewerTab);
                } else if (!shouldShow && !currentlyHidden) {
                    manager.hideNameTag(targetTab, viewerTab);
                }
            }
        }
    }

    private String clanOf(Player player) {
        String placeholder = getConfig().getString("clan-placeholder", "%apex_clan_name%");
        String hasClanPlaceholder = getConfig().getString("has-clan-placeholder", "%apex_has_clan%");
        String parsed;
        try {
            if (hasClanPlaceholder != null && !hasClanPlaceholder.isBlank()) {
                String hasClan = PlaceholderAPI.setPlaceholders(player, hasClanPlaceholder).trim();
                if (hasClan.equalsIgnoreCase("false")) {
                    return "";
                }
            }
            parsed = PlaceholderAPI.setPlaceholders(player, placeholder);
        } catch (Exception ex) {
            getLogger().warning("No se pudo leer el clan de " + player.getName() + ": " + ex.getMessage());
            return "";
        }

        if (parsed == null) {
            return "";
        }

        String clean = ChatColor.stripColor(parsed);
        clean = clean == null ? "" : clean.trim();

        // Si PAPI no resolvió el placeholder, no lo trates como nombre de clan.
        if (clean.equalsIgnoreCase(placeholder)) {
            return "";
        }

        for (String noClan : getConfig().getStringList("no-clan-values")) {
            if (clean.equalsIgnoreCase(noClan.trim())) {
                return "";
            }
        }
        return clean;
    }

    private boolean isPublic(UUID uuid) {
        String path = uuid + ".public";
        if (playersData.contains(path)) {
            return playersData.getBoolean(path);
        }
        return getConfig().getBoolean("default-public", false);
    }

    private void setPublic(UUID uuid, boolean value) {
        playersData.set(uuid + ".public", value);
        savePlayersData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("papucraft.nametag.admin")) {
                send(sender, "no-permission");
                return true;
            }
            reloadConfig();
            startRefreshTask();
            refreshAll();
            send(sender, "reloaded");
            return true;
        }

        if (!(sender instanceof Player player)) {
            send(sender, "player-only");
            return true;
        }

        if (!player.hasPermission("papucraft.nametag.use")) {
            send(player, "no-permission");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            send(player, isPublic(player.getUniqueId()) ? "status-on" : "status-off");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                setPublic(player.getUniqueId(), true);
                send(player, "enabled");
                refreshAll();
            }
            case "off" -> {
                setPublic(player.getUniqueId(), false);
                send(player, "disabled");
                refreshAll();
            }
            case "toggle" -> {
                boolean next = !isPublic(player.getUniqueId());
                setPublic(player.getUniqueId(), next);
                send(player, next ? "enabled" : "disabled");
                refreshAll();
            }
            default -> send(player, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("on", "off", "toggle", "status"));
        if (sender.hasPermission("papucraft.nametag.admin")) {
            options.add("reload");
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.startsWith(typed));
        return options;
    }

    private void send(CommandSender sender, String key) {
        String prefix = getConfig().getString("messages.prefix", "&8[&6PapuCraft&8] &r");
        String text = getConfig().getString("messages." + key, "&cMensaje no configurado: " + key);
        sender.sendMessage(color(prefix + text));
    }

    @SuppressWarnings("deprecation")
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
