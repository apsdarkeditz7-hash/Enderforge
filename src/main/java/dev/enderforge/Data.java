package dev.enderforge;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class Data {
    private final Enderforge plugin;
    private final File file;
    private final YamlConfiguration yml;

    Data(Enderforge plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.file = new File(plugin.getDataFolder(), "data.yml");
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    public double balance(UUID id) { return yml.getDouble("balance." + id, 0); }
    public void addBalance(UUID id, double v) { yml.set("balance." + id, balance(id) + v); }
    public int extraHearts(UUID id) { return yml.getInt("hearts." + id, 0); }
    public void setExtraHearts(UUID id, int v) { yml.set("hearts." + id, v); }
    public String element(UUID id) { return yml.getString("element." + id, "grass"); }
    public void setElement(UUID id, String e) { yml.set("element." + id, e); }

    public void save() {
        try { yml.save(file); }
        catch (IOException e) { plugin.getLogger().warning("Could not save data.yml: " + e.getMessage()); }
    }
}
