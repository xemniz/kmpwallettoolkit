package consumer.smoke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ConsumerSmokeTest {
    @Test
    fun consumesPublishedToolkitCoordinates() {
        val result = ConsumerSmoke().run()

        assertEquals("0x0000000000000000000000000000000000000001", result.address)
        assertEquals("0x020304", result.rawSignedTx)
        assertEquals("test test test test test test test test test test test junk", result.exportedMnemonic)
        assertFalse("test test" in result.toString())
    }
}
