package dev.jdguo.toll;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Vanilla server chat, visible only to the payer. */
final class TollChat {
    private TollChat() {}

    static void send(ServerPlayerEntity player, MinecraftServer server, TollStore.Order order, TollStore.Point point) {
        var balance = BankBridge.accountBalance(player.getUuid(), order.currency).orElse(null);
        String currency = balance != null ? balance.name()
                : point != null && point.currencyName != null && !point.currencyName.isBlank()
                    ? point.currencyName : order.currency;
        var notice = TollNotice.forOrder(order.status, order.lastCode, payee(server, order),
                order.amount, currency, balance == null ? null : balance.amount(),
                order.currency != null && order.currency.startsWith("personal:"));
        var statusColor = switch (notice.state()) {
            case PAID -> Formatting.GREEN;
            case FAILED -> Formatting.RED;
            case UNCONFIRMED -> Formatting.GOLD;
        };
        var line = Text.empty()
                .append(Text.literal("【" + notice.state().label + "】").formatted(statusColor, Formatting.BOLD))
                .append(Text.literal(notice.action()))
                .append(Text.literal(notice.payee()).formatted(Formatting.AQUA))
                .append(Text.literal("支付 "))
                .append(Text.literal(notice.payment()).formatted(Formatting.YELLOW));
        if (!notice.reason().isEmpty()) {
            line.append(Text.literal("；"))
                    .append(Text.literal(notice.reason()).formatted(statusColor));
        }
        line.append(Text.literal("；" + notice.balanceLabel()))
                .append(Text.literal(notice.balance()).formatted(Formatting.YELLOW));
        player.sendMessage(line, false);
    }

    static String payee(MinecraftServer server, TollStore.Order order) {
        if ("system".equals(order.mode)) return "中央银行";
        if (order.recipient != null) {
            try {
                UUID id = UUID.fromString(order.recipient);
                var online = server.getPlayerManager().getPlayer(id);
                if (online != null) return online.getGameProfile().getName();
                var cache = server.getUserCache();
                if (cache != null) return cache.getByUuid(id).map(profile -> profile.getName())
                        .filter(name -> !name.isBlank()).orElse("未记录名称的收款账户");
            } catch (IllegalArgumentException ignored) {
                // Historical malformed data must not interrupt toll processing for other players.
            }
        }
        return "未记录名称的收款账户";
    }
}
