package gg.nodus.gaslight;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GaslightPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Map<String, String>> triggerWords = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, AsyncChatEvent>> spentTriggerWords = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("gaslight").setExecutor(this);
    }

    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Must be player");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /gaslight [word] [message to substitute]");
        }

        final String triggerWord = args[0];
        final String[] joinedArgs = Arrays.copyOfRange(args, 1, args.length);

        final String substitute = String.join(" ", joinedArgs);
        getPlayerTriggerWords(player).put(triggerWord, substitute);
        getPlayerSpentTriggerWords(player).remove(triggerWord);

        sender.sendMessage("§a[Gaslight] §7 Registered \"" + substitute + "\" as a substitute for \"" + triggerWord + "\"");
        return true;
    }

    @EventHandler
    public void onChatMessage(final AsyncChatEvent chatEvent) {
        chatEvent.renderer((source, sourceDisplayName, message, viewer) -> {
            final Map<String, String> playerTriggerWords = getPlayerTriggerWords(source);
            final Map<String, AsyncChatEvent> playerSpentTriggerWords = getPlayerSpentTriggerWords(source);
            if (playerTriggerWords == null) {
                return renderChat(sourceDisplayName, splitInHalf(message));
            }

            final String plaintext = PlainTextComponentSerializer.plainText().serialize(message);
            final String[] words = plaintext.split(" ");

            for (final String word : words) {
                final AsyncChatEvent maybeOtherEvent = playerSpentTriggerWords.get(word);
                if (maybeOtherEvent != null && maybeOtherEvent != chatEvent) {
                    continue;
                }

                final String replacement = playerTriggerWords.get(word);
                if (replacement != null) {
                    playerSpentTriggerWords.put(word, chatEvent);
                    if (viewer == source) {
                        return renderChat(sourceDisplayName, Component.text(replacement, NamedTextColor.RED));
                    }
                    final Component component = Component.text(replacement)
                            .replaceText(TextReplacementConfig.builder()
                                    .match(word)
                                    .replacement(splitInHalf(Component.text(word)))
                                    .build());
                    return renderChat(sourceDisplayName, component);
                }
            }
            return renderChat(sourceDisplayName, splitInHalf(chatEvent.message()));
        });
    }

    private static TextComponent renderChat(final Component sourceDisplayName, final Component displayedMessage) {
        final HoverEvent<Component> hoverEvent = HoverEvent.showText(
                Component.text()
                        .append(Component.translatable("chat.tag.modified"))
                        .append(Component.newline())
                        .append(displayedMessage.style(Style.style(NamedTextColor.GRAY)))
        );

        return Component.text()
                .append(Component.text("<"))
                .append(sourceDisplayName)
                .append(Component.text("> "))
                .append(displayedMessage)
                .hoverEvent(hoverEvent)
                .build();
    }

    private static Component splitInHalf(Component message) {
        if (!(message instanceof TextComponent text)) {
            return message;
        }

        final String content = text.content();
        if (content.length() < 2) {
            return message;
        }

        final int mid = content.length() / 2;

        final Component firstHalf = Component.text(content.substring(0, mid))
                .style(text.style())
                .font(Key.key("minecraft:default"));

        final Component secondHalf = Component.text(content.substring(mid))
                .style(text.style())
                .children(text.children())
                .font(Key.key("minecraft:default"));

        return Component.empty()
                .font(Key.key("minecraft:monospaced"))
                .append(firstHalf)
                .append(Component.text("\u200c", NamedTextColor.DARK_GRAY).font(Key.key("minecraft:default")))
                .append(secondHalf);
    }

    private Map<String, String> getPlayerTriggerWords(final Player player) {
        return triggerWords.computeIfAbsent(player.getUniqueId(), u -> new ConcurrentHashMap<>());
    }

    private Map<String, AsyncChatEvent> getPlayerSpentTriggerWords(final Player player) {
        return spentTriggerWords.computeIfAbsent(player.getUniqueId(), u -> new ConcurrentHashMap<>());
    }

}
