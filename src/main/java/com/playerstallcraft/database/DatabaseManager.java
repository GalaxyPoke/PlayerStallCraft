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
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "PlayerStallCraft-DB");
            t.setDaemon(true);
            return t;
        });
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
                    type VARCHAR(16) NOT NULL DEFAULT 'market',
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(32) NOT NULL DEFAULT '',
                    buyer_uuid VARCHAR(36) NOT NULL,
                    buyer_name VARCHAR(32) NOT NULL DEFAULT '',
                    item_name VARCHAR(128) NOT NULL DEFAULT '',
                    amount INTEGER NOT NULL,
                    price DECIMAL(15,2) NOT NULL,
                    currency_type VARCHAR(32) NOT NULL,
                    tax_amount DECIMAL(15,2) DEFAULT 0,
                    created_at BIGINT NOT NULL DEFAULT 0
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));
            // 兼容旧数据库：补充可能缺失的列
            tryAddColumn(stmt, "transaction_logs", "type", "VARCHAR(16) NOT NULL DEFAULT 'market'");
            tryAddColumn(stmt, "transaction_logs", "seller_name", "VARCHAR(32) NOT NULL DEFAULT ''");
            tryAddColumn(stmt, "transaction_logs", "buyer_name", "VARCHAR(32) NOT NULL DEFAULT ''");
            tryAddColumn(stmt, "transaction_logs", "item_name", "VARCHAR(128) NOT NULL DEFAULT ''");

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
                    shelf_count INTEGER DEFAULT 1,
                    min_x DOUBLE,
                    min_y DOUBLE,
                    min_z DOUBLE,
                    max_x DOUBLE,
                    max_y DOUBLE,
                    max_z DOUBLE,
                    type VARCHAR(16) DEFAULT 'rent',
                    expire_time BIGINT DEFAULT 0,
                    shelf_durability INTEGER DEFAULT 1000,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // 为旧数据库添加缺失的列
            tryAddColumn(stmt, "shops", "shelf_count", "INTEGER DEFAULT 1");
            tryAddColumn(stmt, "shops", "min_x", "DOUBLE");
            tryAddColumn(stmt, "shops", "min_y", "DOUBLE");
            tryAddColumn(stmt, "shops", "min_z", "DOUBLE");
            tryAddColumn(stmt, "shops", "max_x", "DOUBLE");
            tryAddColumn(stmt, "shops", "max_y", "DOUBLE");
            tryAddColumn(stmt, "shops", "max_z", "DOUBLE");
            tryAddColumn(stmt, "shops", "owner_name", "VARCHAR(16)");
            tryAddColumn(stmt, "shops", "is_rented", "BOOLEAN DEFAULT 0");
            tryAddColumn(stmt, "shops", "is_owned", "BOOLEAN DEFAULT 0");
            tryAddColumn(stmt, "shops", "rent_expire_time", "BIGINT DEFAULT 0");

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

            // 货架表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shop_shelves (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    shop_id INTEGER NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // 货架商品表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shelf_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    shelf_id INTEGER NOT NULL,
                    slot INTEGER NOT NULL,
                    item_data TEXT NOT NULL,
                    price DECIMAL(15,2) NOT NULL,
                    stock INTEGER NOT NULL,
                    sold INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (shelf_id) REFERENCES shop_shelves(id) ON DELETE CASCADE
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // 为shops表添加已解锁货架数列
            tryAddColumn(stmt, "player_data", "total_sold_count", "INTEGER DEFAULT 0");
            tryAddColumn(stmt, "shops", "unlocked_shelf_slots", "INTEGER DEFAULT 1");
            
            // 为shop_shelves表添加显示名称列
            tryAddColumn(stmt, "shop_shelves", "display_name", "VARCHAR(128) DEFAULT NULL");
            
            // 为shelf_items表添加货币类型列
            tryAddColumn(stmt, "shelf_items", "currency_type", "VARCHAR(16) DEFAULT 'nye'");
            // 促销折扣列
            tryAddColumn(stmt, "shelf_items", "discount_rate", "REAL DEFAULT 1.0");
            tryAddColumn(stmt, "shelf_items", "discount_expiry", "BIGINT DEFAULT 0");

            // 为buy_requests表添加新功能列（兼容旧数据库）
            tryAddColumn(stmt, "buy_requests", "expire_time", "BIGINT DEFAULT 0");
            tryAddColumn(stmt, "buy_requests", "remaining_amount", "INTEGER DEFAULT 0");
            tryAddColumn(stmt, "buy_requests", "is_featured", "INTEGER DEFAULT 0");
            tryAddColumn(stmt, "buy_requests", "featured_until", "BIGINT DEFAULT 0");

            // 广告位表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ad_slots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name VARCHAR(64) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    price_per_hour DECIMAL(15,2) DEFAULT 100,
                    currency_type VARCHAR(16) DEFAULT 'nye',
                    active BOOLEAN DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));

            // 广告表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS advertisements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    slot_id INTEGER NOT NULL,
                    owner_uuid VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(32) NOT NULL,
                    title VARCHAR(64) NOT NULL,
                    description VARCHAR(256),
                    icon_material VARCHAR(64) DEFAULT 'GOLD_INGOT',
                    shop_id INTEGER DEFAULT 0,
                    start_time BIGINT NOT NULL,
                    end_time BIGINT NOT NULL,
                    price DECIMAL(15,2) NOT NULL,
                    currency_type VARCHAR(16) DEFAULT 'nye',
                    active BOOLEAN DEFAULT 1,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (slot_id) REFERENCES ad_slots(id) ON DELETE CASCADE
                )
            """.replace("AUTOINCREMENT", dbType.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT"));
        }
    }

    private void tryAddColumn(Statement stmt, String table, String column, String type) {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (Exception ignored) {
            // 列已存在，忽略错误
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

    public CompletableFuture<Integer> executeAsyncGetId(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement stmt = getConnection().prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("执行SQL失败: " + e.getMessage());
            }
            return -1;
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
