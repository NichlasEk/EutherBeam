package se.euther.eutherbeam.discovery

import kotlinx.coroutines.runBlocking
import org.junit.Test

class SamsungWakeOnLanLiveTest {
    @Test
    fun sendsMagicPacketWhenLiveMacIsConfigured() = runBlocking {
        val mac = System.getenv("EUTHERBEAM_SAMSUNG_MAC") ?: return@runBlocking
        val broadcast = System.getenv("EUTHERBEAM_SAMSUNG_BROADCAST")

        WakeOnLan.send(mac, broadcast)
    }
}
