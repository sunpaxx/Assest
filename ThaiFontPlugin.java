package th.thaifont;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

public final class ThaiFontPlugin extends JavaPlugin implements Listener {

    // ไอดีคงที่ของ pack: ถ้าเปลี่ยน pack แล้วส่งใหม่ ไคลเอนต์จะแทนที่ตัวเก่าให้เอง
    private static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("thaifont-itim".getBytes(StandardCharsets.UTF_8));

    private final MiniMessage mm = MiniMessage.miniMessage();
    private ThaiShaper shaper;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try (InputStream in = getResource("rules.txt")) {
            shaper = ThaiShaper.load(in);
            getLogger().info("โหลดกฎจัดตำแหน่งสระ/วรรณยุกต์ " + shaper.ruleCount() + " รายการ");
        } catch (IOException | RuntimeException ex) {
            getLogger().severe("โหลด rules.txt ไม่สำเร็จ: " + ex);
        }
        getServer().getPluginManager().registerEvents(this, this);
        if (getConfig().getString("pack-url", "").contains("example.com")) {
            getLogger().warning("ยังไม่ได้ตั้ง pack-url ใน config.yml — ผู้เล่นจะโหลดฟอนต์ไม่ได้");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sendPack(event.getPlayer());
    }

    // สลับตัวอักษรในแชต เพื่อให้สระ/วรรณยุกต์ไม่ซ้อนกัน
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (shaper == null || !getConfig().getBoolean("shape-chat", true)) return;
        ChatRenderer previous = event.renderer();
        event.renderer((source, name, message, viewer) ->
                previous.render(source, name, shaper.shape(message), viewer));
    }

    // ป้าย sign
    @EventHandler(ignoreCancelled = true)
    public void onSign(SignChangeEvent event) {
        if (shaper == null || !getConfig().getBoolean("shape-signs", true)) return;
        for (int i = 0; i < 4; i++) {
            Component line = event.line(i);
            if (line != null) event.line(i, shaper.shape(line));
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!PACK_ID.equals(event.getID())) return;
        if (!getConfig().getBoolean("kick-if-failed", false)) return;

        switch (event.getStatus()) {
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD ->
                    event.getPlayer().kick(mm.deserialize(
                            getConfig().getString("kick-message", "Resource pack required")));
            default -> { }
        }
    }

    private void sendPack(Player player) {
        String url = getConfig().getString("pack-url", "");
        if (url.isBlank()) return;

        byte[] hash = null;
        String sha1 = getConfig().getString("pack-sha1", "").trim();
        if (sha1.length() == 40) {
            try {
                hash = HexFormat.of().parseHex(sha1);
            } catch (IllegalArgumentException ex) {
                getLogger().warning("pack-sha1 ไม่ใช่เลขฐาน 16 ที่ถูกต้อง จะส่งโดยไม่มี hash");
            }
        }

        Component prompt = mm.deserialize(getConfig().getString("prompt", ""));
        boolean force = getConfig().getBoolean("force", false);

        player.setResourcePack(PACK_ID, url, hash, prompt, force);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("/thaifont <reload|send>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("โหลด config ใหม่แล้ว");
            }
            case "send" -> {
                if (args.length >= 2) {
                    Player target = getServer().getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage("ไม่พบผู้เล่นคนนี้");
                        return true;
                    }
                    sendPack(target);
                    sender.sendMessage("ส่ง pack ให้ " + target.getName() + " แล้ว");
                } else {
                    getServer().getOnlinePlayers().forEach(this::sendPack);
                    sender.sendMessage("ส่ง pack ให้ทุกคนที่ออนไลน์แล้ว");
                }
            }
            default -> sender.sendMessage("/thaifont <reload|send [ชื่อผู้เล่น]>");
        }
        return true;
    }
}
