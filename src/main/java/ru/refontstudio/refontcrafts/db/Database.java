package ru.refontstudio.refontcrafts.db;

import org.bukkit.configuration.file.FileConfiguration;
import ru.refontstudio.refontcrafts.RefontCrafts;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class Database {
    private final RefontCrafts plugin;
    private String configuredType;
    private String activeType;
    private String url;
    private String user;
    private String pass;

    public Database(RefontCrafts plugin) {
        this(plugin, plugin.getConfig().getString("database.type", "sqlite"));
    }

    public Database(RefontCrafts plugin, String forcedType) {
        this.plugin = plugin;
        this.configuredType = forcedType == null ? "sqlite" : forcedType.toLowerCase();
        buildForType(this.configuredType);
        this.activeType = this.configuredType;
    }

    public static Database ofType(RefontCrafts plugin, String type) {
        return new Database(plugin, type);
    }

    private void buildForType(String type) {
        FileConfiguration c = plugin.getConfig();
        if ("mysql".equalsIgnoreCase(type)) {
            String hostRaw = c.getString("database.mysql.host", "127.0.0.1");
            int portCfg = c.getInt("database.mysql.port", 3306);
            String host = hostRaw;
            int port = portCfg;
            if (hostRaw != null && hostRaw.contains(":")) {
                String[] parts = hostRaw.split(":");
                host = parts[0].trim();
                try { port = Integer.parseInt(parts[1].trim()); } catch (Throwable ignored) { port = portCfg; }
            }
            String db = c.getString("database.mysql.database", "refontcrafts");
            boolean useSSL = c.getBoolean("database.mysql.use_ssl", false);
            String params = c.getString("database.mysql.params", "useUnicode=true&characterEncoding=utf8");
            String qp = "useSSL=" + useSSL + (params == null || params.isEmpty() ? "" : "&" + params);
            this.url = "jdbc:mysql://" + host + ":" + port + "/" + db + "?" + qp;
            this.user = c.getString("database.mysql.user", "root");
            this.pass = c.getString("database.mysql.password", "");
        } else {
            String file = c.getString("database.sqlite.file", "data.db");
            File f = new File(plugin.getDataFolder(), file);
            this.url = "jdbc:sqlite:" + f.getAbsolutePath();
            this.user = null;
            this.pass = null;
        }
    }

    private void buildFailoverSqlite() {
        File f = new File(plugin.getDataFolder(), "failover.db");
        this.url = "jdbc:sqlite:" + f.getAbsolutePath();
        this.user = null;
        this.pass = null;
        this.activeType = "sqlite";
    }

    public String getType() { return configuredType; }
    public String getActiveType() { return activeType; }
    public boolean isFailoverActive() { return !"mysql".equalsIgnoreCase(configuredType) ? false : !"mysql".equalsIgnoreCase(activeType); }

    public Connection getConnection() throws java.sql.SQLException {
        if (user == null) return DriverManager.getConnection(url);
        return DriverManager.getConnection(url, user, pass);
    }

    public boolean ensureReadyWithRetry(int attempts, long delayMs) {
        for (int i = 0; i < attempts; i++) {
            try (Connection cn = getConnection()) { return true; }
            catch (Throwable ignored) {}
            try { Thread.sleep(Math.max(0, delayMs)); } catch (InterruptedException ignored) {}
        }
        return false;
    }

    public boolean activateFailoverSqlite() {
        if ("sqlite".equalsIgnoreCase(activeType)) return true;
        buildFailoverSqlite();
        try { init(); } catch (Throwable ignored) {}
        return true;
    }

    public void init() {
        try (Connection cn = getConnection(); Statement st = cn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS shapeless_recipes (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "result VARCHAR(64) NOT NULL," +
                    "created_at BIGINT NOT NULL" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS shapeless_ingredients (" +
                    "recipe_id VARCHAR(64) NOT NULL," +
                    "ord INT NOT NULL," +
                    "item VARCHAR(64) NOT NULL," +
                    "PRIMARY KEY (recipe_id, ord)" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS anvil_recipes (" +
                    "id VARCHAR(64) PRIMARY KEY," +
                    "left_item VARCHAR(64) NOT NULL," +
                    "right_item VARCHAR(64) NOT NULL," +
                    "result VARCHAR(64) NOT NULL," +
                    "cost INT NOT NULL," +
                    "created_at BIGINT NOT NULL" +
                    ")");
        } catch (Throwable ignored) {}
    }
}