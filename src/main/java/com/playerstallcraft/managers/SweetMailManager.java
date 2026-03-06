package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * SweetMail 软依赖管理器。
 * 不直接导入 SweetMail 类，通过 SweetMailBridge 隔离，
 * 确保 SweetMail 未安装时不会抛出 NoClassDefFoundError。
 */
public class SweetMailManager {

    private final SweetMailBridge bridge;

    public SweetMailManager(PlayerStallCraft plugin) {
        SweetMailBridge b = null;
        if (Bukkit.getPluginManager().isPluginEnabled("SweetMail")) {
            try {
                b = new SweetMailBridge(plugin);
                plugin.getLogger().info("已检测到 SweetMail，邮件功能已启用！");
            } catch (Exception e) {
                plugin.getLogger().warning("SweetMail 初始化失败: " + e.getMessage());
            }
        }
        this.bridge = b;
    }

    public boolean isEnabled() {
        return bridge != null;
    }

    /**
     * 向指定玩家发送带物品附件的系统邮件（用于购买后物品投递）。
     *
     * @param receiverUuid 收件人 UUID
     * @param item         要投递的物品
     * @param title        邮件标题
     * @param contentLines 邮件正文
     * @return 是否成功发送
     */
    public boolean sendItemMail(UUID receiverUuid, ItemStack item, String title, String... contentLines) {
        if (bridge == null) return false;
        return bridge.sendItemMail(receiverUuid, item, title, contentLines);
    }

    /**
     * 向指定玩家发送纯文本系统通知邮件（无附件）。
     *
     * @param receiverUuid 收件人 UUID
     * @param title        邮件标题
     * @param contentLines 邮件正文
     * @return 是否成功发送
     */
    public boolean sendNoticeMail(UUID receiverUuid, String title, String... contentLines) {
        if (bridge == null) return false;
        return bridge.sendNoticeMail(receiverUuid, title, contentLines);
    }
}
