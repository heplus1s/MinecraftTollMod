package dev.jdguo.toll;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TollNoticeTest {
    @Test void paidUsesOriginalTenderAndExactBalance() {
        var notice = TollNotice.forOrder("PAID", "ok", "中央银行", "0.0123", "央行券", new BigDecimal("25.0001"), false);
        assertEquals(TollNotice.State.PAID, notice.state());
        assertEquals("已向", notice.action());
        assertEquals("中央银行", notice.payee());
        assertEquals("0.0123 央行券", notice.payment());
        assertEquals("25.0001 央行券", notice.balance());
    }
    @Test void insufficientPersonalBalanceShowsTotalAsIncludingListings() {
        var notice = TollNotice.forOrder("INSUFFICIENT", "insufficient_funds", "Alice", "10", "星币", new BigDecimal("11"), true);
        assertEquals(TollNotice.State.FAILED, notice.state());
        assertEquals("可用余额不足，未扣款", notice.reason());
        assertEquals("当前账面余额（含挂牌）：", notice.balanceLabel());
    }
    @Test void unavailableIsDifferentFromZeroAndUnconfirmedIsNotFailure() {
        var pending = TollNotice.forOrder("PENDING", "save_failed", "中央银行", "1", "钻石", null, false);
        assertEquals("暂不可查询", pending.balance());
        assertEquals("结果尚未确认", pending.reason());
        assertEquals(TollNotice.State.UNCONFIRMED, pending.state());
        var conflict = TollNotice.forOrder("REVIEW", "reference_conflict", "Alice", "1", "钻石", BigDecimal.ZERO, false);
        assertEquals(TollNotice.State.UNCONFIRMED, conflict.state());
        assertEquals("0 钻石", conflict.balance());
    }
}
