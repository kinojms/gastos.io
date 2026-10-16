package io.gastos.app.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object BackupSerializer {
    const val CURRENT = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true // schemaVersion must always be written
    }

    fun encode(f: BackupFile): String = json.encodeToString(BackupFile.serializer(), f)

    /**
     * Reads the schema version first, so a newer file is refused with a clear message
     * even if its shape no longer matches [BackupFile].
     */
    fun decode(json: String): BackupFile {
        val root = try {
            this.json.parseToJsonElement(json).jsonObject
        } catch (e: IllegalArgumentException) { // SerializationException is a subclass
            throw SerializationException("Not a Gastos.io backup.", e)
        }
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: throw SerializationException("Missing schemaVersion.")
        if (version > CURRENT) {
            throw UnsupportedBackupException("This backup is from a newer version of Gastos.io. Update the app first.")
        }
        // Older versions would be migrated here; version 1 is the first.
        return this.json.decodeFromJsonElement(BackupFile.serializer(), root)
    }
}
