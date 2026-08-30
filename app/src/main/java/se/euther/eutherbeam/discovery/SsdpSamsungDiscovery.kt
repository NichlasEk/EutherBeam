package se.euther.eutherbeam.discovery

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class SsdpSamsungDiscovery(
    context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build(),
) {
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    suspend fun discover(timeoutMillis: Long = 5_000): List<SamsungTvDevice> = withContext(Dispatchers.IO) {
        val lock = wifiManager?.createMulticastLock("EutherBeam:ssdp")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            discoverLocations(timeoutMillis)
                .mapNotNull(::loadSamsungDevice)
                .distinctBy { it.deviceId.ifBlank { it.address } }
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }

    private fun discoverLocations(timeoutMillis: Long): Set<String> {
        val request = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: ssdp:all\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)

        val locations = linkedSetOf<String>()
        DatagramSocket().use { socket ->
            socket.reuseAddress = true
            socket.soTimeout = 500
            val destination = InetAddress.getByName("239.255.255.250")
            repeat(2) {
                socket.send(DatagramPacket(request, request.size, destination, 1900))
            }

            val deadline = System.nanoTime() + timeoutMillis * 1_000_000
            val buffer = ByteArray(16 * 1024)
            while (System.nanoTime() < deadline) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val response = packet.data.decodeToString(packet.offset, packet.offset + packet.length)
                    val headers = SamsungDeviceParser.parseHeaders(response)
                    if (SamsungDeviceParser.isSamsungResponse(headers)) {
                        headers["location"]?.let(locations::add)
                    }
                } catch (_: SocketTimeoutException) {
                    // Keep listening until the overall deadline; devices answer at different times.
                }
            }
        }
        return locations
    }

    private fun loadSamsungDevice(location: String): SamsungTvDevice? {
        val host = runCatching { java.net.URI(location).host }.getOrNull() ?: return null
        val infoUrl = "http://$host:8001/ms/1.0/"
        val request = Request.Builder().url(infoUrl).get().build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                SamsungDeviceParser.parseDeviceInfo(host, body)
            }
        }.getOrNull()
    }
}
