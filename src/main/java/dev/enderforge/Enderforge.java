package dev.enderforge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Enderforge extends JavaPlugin implements TabExecutor {
    private Cooldowns cooldowns;
    private Data data;
    private final Map<String, EfModule> modules = new LinkedHashMap<>();
    private final Map<String, EfModule> byCommand = new HashMap<>();

    public Cooldowns cooldowns() { return cooldowns; }
    public Data data() { return data; }
    public EfModule module(String id) { return modules.get(id); }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cooldowns = new Cooldowns();
        data = new Data(this);

        register(new ElementalSwitch(this));
        register(new GrassPower(this));
        register(new EarthquakePower(this));
        register(new MetalPower(this));
        register(new FireWaterPower(this));
        register(new MoneyMaker(this));
        register(new AutoMiner(this));
        register(new AutoFarmer(this));
        register(new InfinityGoldMaker(this));
        register(new AutoLumberjack(this));
        register(new AutoFisher(this));
        register(new HeartIncreaser(this));
        register(new XpBooster(this));
        register(new FlyModule(this));
        register(new Smelter(this));

        for (EfModule m : modules.values()) {
            if (getConfig().getBoolean("modules." + m.id() + ".enabled", true)) m.enable();
            for (String c : m.commands()) byCommand.put(c, m);
        }
        for (String c : byCommand.keySet()) bind(c);
        bind("enderforge");

        Bukkit.getScheduler().runTaskTimer(this, () -> data.save(), 6000L, 6000L);
        getLogger().info("Enderforge enabled with " + modules.size() + " modules.");
    }

    @Override
    public void onDisable() {
        modules.values().forEach(EfModule::disable);
        if (data != null) data.save();
    }

    private void register(EfModule m) { modules.put(m.id(), m); }

    private void bind(String name) {
        var cmd = getCommand(name);
        if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String name = c.getName().toLowerCase();
        if (name.equals("enderforge")) return admin(s, a);

        EfModule m = byCommand.get(name);
        if (m == null) return false;
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (!m.isEnabled()) {
            p.sendMessage(Component.text("That module is disabled.", NamedTextColor.RED));
            return true;
        }
        return m.onCommand(p, a);
    }

    private boolean admin(CommandSender s, String[] a) {
        String sub = a.length == 0 ? "list" : a[0].toLowerCase();
        switch (sub) {
            case "list" -> {
                s.sendMessage(Component.text("Enderforge modules:", NamedTextColor.LIGHT_PURPLE));
                for (EfModule m : modules.values())
                    s.sendMessage(Component.text((m.isEnabled() ? " [ON]  " : " [OFF] ") + m.id()
                            + " - " + m.cooldownSeconds() + "s", m.isEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED));
            }
            case "toggle" -> {
                if (!s.hasPermission("enderforge.admin")) { s.sendMessage("No permission."); return true; }
                EfModule m = a.length > 1 ? modules.get(a[1].toLowerCase()) : null;
                if (m == null) { s.sendMessage("Usage: /enderforge toggle <module>"); return true; }
                boolean now = !m.isEnabled();
                getConfig().set("modules." + m.id() + ".enabled", now);
                saveConfig();
                if (now) m.enable(); else m.disable();
                s.sendMessage(Component.text(m.id() + " is now " + (now ? "ON" : "OFF"), NamedTextColor.GOLD));
            }
            case "reload" -> {
                if (!s.hasPermission("enderforge.admin")) { s.sendMessage("No permission."); return true; }
                reloadConfig();
                for (EfModule m : modules.values()) {
                    m.disable();
                    if (getConfig().getBoolean("modules." + m.id() + ".enabled", true)) m.enable();
                }
                s.sendMessage(Component.text("Enderforge reloaded.", NamedTextColor.GOLD));
            }
            default -> s.sendMessage("Usage: /enderforge <list|toggle|reload>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        String name = c.getName().toLowerCase();
        if (name.equals("enderforge")) {
            if (a.length == 1) return List.of("list", "toggle", "reload");
            if (a.length == 2 && a[0].equalsIgnoreCase("toggle")) return new ArrayList<>(modules.keySet());
            return List.of();
        }
        EfModule m = byCommand.get(name);
        return m == null ? List.of() : m.tab(a);
    }
}
