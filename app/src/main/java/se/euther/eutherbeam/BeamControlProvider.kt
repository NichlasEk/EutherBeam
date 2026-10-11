package se.euther.eutherbeam

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import se.euther.eutherbeam.androidtv.AndroidTvDeviceStore
import se.euther.eutherbeam.androidtv.bindPlayer

class BeamControlProvider : ContentProvider() {
    private val remote by lazy { BeamVoice(requireNotNull(context)) }
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        requireNotNull(context).enforceCallingOrSelfPermission(BeamLink.PERMISSION, "EutherBeam control permission required")
        val target = extras?.getString("target").orEmpty()
        val command = extras?.getString("command").orEmpty()
        return runCatching {
            runBlocking {
                val result = Bundle()
                val message = when (method) {
                    "control" -> remote.execute(target, command)
                    "status" -> remote.status(target)
                    "search" -> {
                        val pcm = extras?.getByteArray("pcm") ?: error("Sökljud saknas")
                        require(pcm.size in 2..400_000)
                        remote.search(target, command, pcm)
                    }
                    "players" -> {
                        result.putString("players", Gson().toJson(AndroidTvDeviceStore(requireNotNull(context)).load()))
                        ""
                    }
                    "bind" -> {
                        val store = AndroidTvDeviceStore(requireNotNull(context))
                        store.save(bindPlayer(store.load(), target, command.ifBlank { null }))
                        "Kopplingen är sparad i EutherBeam."
                    }
                    else -> error("Okänt EutherBeam-kommando")
                }
                result.apply { putBoolean("ok", true); putString("message", message) }
            }
        }.getOrElse { Bundle().apply { putBoolean("ok", false); putString("message", it.message ?: "EutherBeam-kommandot misslyckades") } }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
