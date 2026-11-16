package com.example.picup.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("picup-settings")

/**
 * The single source of truth for [AppSettings], persisted as one JSON document
 * inside DataStore.
 *
 * One document instead of a key per field keeps migration cheap: every field
 * has a default, an old document simply lacks the new keys, and R8 keeps the
 * serializer. DataStore gives the atomic write and the change Flow.
 */
class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val json = AtomicJsonFile.lenientJson

    val settings: Flow<AppSettings> = appContext.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> decode(prefs[SETTINGS_KEY]) }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = decode(appContext.settingsDataStore.data.first()[SETTINGS_KEY])

    /** Read-modify-write of the whole document; the transform returns what is stored. */
    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        var stored = AppSettings()
        appContext.settingsDataStore.edit { prefs ->
            val current = decode(prefs[SETTINGS_KEY])
            stored = transform(current).normalized()
            prefs[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), stored)
        }
        return stored
    }

    suspend fun setActiveRemote(remoteId: String) {
        update { it.copy(activeRemoteId = remoteId) }
    }

    suspend fun setActiveFormat(formatId: String) {
        update { it.copy(activeFormatId = formatId) }
    }

    suspend fun upsertAccount(account: AccountProfile) {
        update { current ->
            val existing = current.accounts.indexOfFirst { it.id == account.id }
            val accounts = current.accounts.toMutableList()
            if (existing >= 0) accounts[existing] = account else accounts += account
            current.copy(accounts = accounts)
        }
    }

    suspend fun upsertRemote(remote: Remote, makeActive: Boolean = false) {
        update { current ->
            val existing = current.remotes.indexOfFirst { it.id == remote.id }
            val remotes = current.remotes.toMutableList()
            if (existing >= 0) remotes[existing] = remote else remotes += remote
            current.copy(
                remotes = remotes,
                activeRemoteId = when {
                    makeActive -> remote.id
                    current.activeRemoteId.isBlank() -> remote.id
                    else -> current.activeRemoteId
                },
            )
        }
    }

    suspend fun removeRemote(remoteId: String) {
        update { current ->
            current.copy(
                remotes = current.remotes.filterNot { it.id == remoteId },
                activeRemoteId = if (current.activeRemoteId == remoteId) "" else current.activeRemoteId,
            )
        }
    }

    /**
     * Removes the account and every repository that belongs to it. The caller
     * deletes the token from the [TokenStore] — this layer does not know it.
     */
    suspend fun removeAccount(accountId: String): List<Remote> {
        var orphaned: List<Remote> = emptyList()
        update { current ->
            orphaned = current.remotes.filter { it.accountId == accountId }
            val remainingRemotes = current.remotes.filterNot { it.accountId == accountId }
            current.copy(
                accounts = current.accounts.filterNot { it.id == accountId },
                remotes = remainingRemotes,
                activeRemoteId = if (remainingRemotes.any { it.id == current.activeRemoteId }) {
                    current.activeRemoteId
                } else {
                    ""
                },
            )
        }
        return orphaned
    }

    private fun decode(raw: String?): AppSettings {
        if (raw.isNullOrBlank()) return AppSettings()
        return runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
            .getOrElse { AppSettings() }
            .normalized()
    }

    private companion object {
        val SETTINGS_KEY = stringPreferencesKey("settings_json")
    }
}