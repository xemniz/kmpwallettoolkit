package xyz.wallet.toolkit.sample.format

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.wallet.toolkit.sample.flows.swap.rawToAmount

class ReviewAmountTest {
    @Test
    fun feesAndTradeAmountsKeepEveryBaseUnit() {
        assertEquals("21000000021000", EthFormat.multiplyDecimalIntegers("21000", "1000000001"))
        assertEquals("0.000021000000021", EthFormat.formatWeiAsEth("21000000021000", 18))
        assertEquals("9876543120987654312098765431200000", EthFormat.multiplyDecimalIntegers("123456789012345678901234567890", "80000"))
        assertEquals("470000000000000", EthFormat.addWei("160000000000000", "310000000000000"))
        assertEquals("0.000000000000000001", rawToAmount("1", 18, 18))
        assertEquals("123", rawToAmount("123", 0, 0))
    }
}
