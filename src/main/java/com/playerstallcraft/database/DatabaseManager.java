package com.playerstallcraft.database;

import com.playerstallcraft.PlayerStallCraft;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {

    private final PlayerStallCraft plugin;
    private Connection connection;
    private final ExecutorService executor;
    private final String dbType;

    public DatabaseManager(PlayerStallCraft plugin) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(2);
        this.dbType = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
    }

    public boolean initialize() {
        try {
            if (dbType.equals("mysql")) {
                initMySQL();
            } else {
                initSQLite();
            }
            createTables();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("数据库连接失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void initSQLite() throws SQLException {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        String url = "jdbc:sqlite:" + new File(dataFolder, "data.db").getAbsolutePath();
        connection = DriverManager.getConnection(url);
        plugin.getLogger().info("已连接到SQLite数据库");
    }

    private void initMySQL() throws SQLException {
        String host = plugin.getConfig().getString("database.mysql.host", "localhost");
        int port = plugin.getConfig().getInt("database.mysql.port", 3306);
        String database = plugin.getConfig().getString("database.mysql.database", "playerstallcraft");
        String username = plugin.getConfig().getString("database.mysql.username", "root");
        String password = plugin.getConfig().getString("database.mysql.password", "");

        String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8",
                host, port, database);
        connection = DriverManager.getConnection(url, username, password);
        plugin.getLogger().info("已连接到MySQL数据库");
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // 摆摊区域表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stall_regions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name VARCHAR(64) UNIQUE NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x1 INTEGER NOT NULL,
                    y1 INTEGER NOT NULL,
                    z1 INTEGER NOT NULL,
                    x2 INTEGER NOT NULL,
                    y2 INTEGER NOT NULL,
                    z2 INTEGER NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // 玩家数据表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_data (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    license_expire_time BIGINT DEFAULT 0,
                    shop_id INTEGER DEFAULT NULL,
                    total_sales DECIMAL(15,2) DEFAULT 0,
                    total_purchases DECIMAL(15,2) DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // 商品上架表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stall_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid VARCHAR(36) NOT NULL,
                    item_data TEXT NOT NULL,
                    price DECIMAL(15,2) NOT NULL,
                    currency_type VARCHAR(32) DEFAULT 'vault',
                    amount INTEGER NOT NULL,
                    slot INTEGER NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // 交易记录表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transaction_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller_uuid VARCHAR(36) NOT NULL,
                    buyer_uuid VARCHAR(36) NOT NULL,
                    item_data TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    price DECIMAL(15,2) NOT NULL,
                    currency_type VARCHAR(32) NOT NULL,
                    tax_amount DECIMAL(15,2) DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // 商铺表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shops (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name VARCHAR(64) NOT NULL,
                    owner_uuid VARCHAR(36),
                    world VARCHAR(64) NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    type VARCHAR(16) DEFAULT 'rent',
                    expire_time BIGINT DEFAULT 0,
                    shelf_durability INTEGER DEFAULT 1000,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // 营业执照表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS licenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(16) NOT NULL,
                    purchase_time BIGINT NOT NULL,
                    expire_time BIGINT NOT NULL,
                    active INTEGER DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // 求购表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS buy_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid VARCHAR(36) NOT NULL,
                    item_type VARCHAR(128) NOT NULL,
                    item_data TEXT,
                    amount INTEGER NOT NULL,
                    price DECIMAL(15,2) NOT NULL,
                    currency_type VARCHAR(32) DEFAULT 'vault',
                    status VARCHAR(16) DEFAULT 'active',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // 全服市场表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS global_market (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(16) NOT NULL,
                    item_data TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    price DECIMAL(15,2) NOT NULL,
                    currency_type VARCHAR(32) DEFAULT 'vault',
                    expire_time BIGINT NOT NULL,
                    status VARCHAR(16) DEFAULT 'active',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                if (dbType.equals("mysql")) {
                    initMySQL();
                } else {
                    initSQLite();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("重新连接数据库失败: " + e.getMessage());
        }
        return connection;
    }

    public CompletableFuture<Void> executeAsync(String sql, Object... params) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("执行SQL失败: " + e.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<ResultSet> queryAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PreparedStatement stmt = getConnection().prepareStatement(sql);
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                return stmt.executeQuery();
            } catch (SQLException e) {
                plugin.getLogger().severe("查询SQL失败: " + e.getMessage());
                return null;
            }
        }, executor);
    }

    public void close() {
        executor.shutdown();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("关闭数据库连接失败: " + e.getMessage());
        }
    }
}
