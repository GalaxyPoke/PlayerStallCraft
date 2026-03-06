package com.playerstallcraft.managers;

import com.playerstallcraft.PlayerStallCraft;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import top.mrxiaom.sweetmail.IMail;
import top.mrxiaom.sweetmail.attachments.AttachmentItem;

import java.util.Arrays;
import java.util.UUID;

/**
 * 实际调用 SweetMail API 的桥接类。
 * 此类只有在 SweetMail 已加载时才会被实例化，
 * 从而避免 SweetMail 未安装时的 NoClassDefFoundError。
 */
public class SweetMailBridge {

    private final PlayerStallCraft plugin;

    public SweetMailBridge(PlayerStallCraft plugin) {
        this.plugin = plugin;
    }

    public boolean sendItemMail(UUID receiverUuid, ItemStack item, String title, String[] contentLines) {
        try {
            OfflinePlayer receiver = Bukkit.getOfflinePlayer(receiverUuid);
            IMail.api()
                    .createSystemMail("摆摊系统")
                    .setReceiverFromPlayer(receiver)
                    .setIcon(item.getType().name())
                    .setTitle(title)
                    .addContent(Arrays.asList(contentLines))
                    .addAttachments(AttachmentItem.build(item))
                    .send();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("SweetMail 发送物品邮件失败: " + e.getMessage());
            return false;
        }
    }

    public boolean sendNoticeMail(UUID receiverUuid, String title, String[] contentLines) {
        try {
            OfflinePlayer receiver = Bukkit.getOfflinePlayer(receiverUuid);
            IMail.api()
                    .createSystemMail("摆摊系统")
                    .setReceiverFromPlayer(receiver)
                    .setIcon("PAPER")
                    .setTitle(title)
                    .addContent(Arrays.asList(contentLines))
                    .send();
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("SweetMail 发送通知邮件失败: " + e.getMessage());
            return false;
        }
    }
}
