package dev.jdguo.toll;

import java.math.BigDecimal;

/** User-facing receipt copy, independent of Minecraft's presentation layer. */
record TollNotice(State state, String action, String payee, String payment, String reason,
                  String balanceLabel, String balance) {
    enum State {
        PAID("扣费成功"), FAILED("扣费失败"), UNCONFIRMED("扣费待确认");
        final String label;
        State(String label) { this.label = label; }
    }

    static TollNotice forOrder(String status, String code, String payee, String amount,
                               String currency, BigDecimal balance, boolean personal) {
        State state = "PAID".equals(status) ? State.PAID
                : "PENDING".equals(status) || "reference_conflict".equals(code)
                    ? State.UNCONFIRMED : State.FAILED;
        String action = switch (state) {
            case PAID -> "已向";
            case FAILED, UNCONFIRMED -> "应向";
        };
        String reason = switch (state) {
            case PAID -> "";
            case UNCONFIRMED -> "结果尚未确认";
            case FAILED -> switch (code == null ? "" : code) {
                case "insufficient_funds" -> "可用余额不足，未扣款";
                case "insufficient_treasury", "insufficient_backing", "no_current_rate",
                    "below_precision", "unsupported_settlement" -> "暂不满足结算条件，未扣款";
                default -> "银行拒绝本次交易，未扣款";
            };
        };
        return new TollNotice(state, action, payee, amount + " " + currency, reason,
                personal ? "当前账面余额（含挂牌）：" : "当前余额：",
                balance == null ? "暂不可查询" : balance.stripTrailingZeros().toPlainString() + " " + currency);
    }
}
