package com.example.picup.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.picup.AppContainer
import com.example.picup.data.AccountProfile
import com.example.picup.data.AppSettings
import com.example.picup.data.CopyFormat
import com.example.picup.data.CopyFormatDefaults
import com.example.picup.data.Remote
import com.example.picup.data.ThemeChoice
import com.example.picup.data.UploadOptions
import com.example.picup.git.GitClients
import com.example.picup.git.plugins.GitHostPlugins
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** All settings mutations funnel through [com.example.picup.data.SettingsRepository.update]. */
class SettingsViewModel(private val app: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun updateUpload(transform: (UploadOptions) -> UploadOptions) {
        viewModelScope.launch { app.settings.update { it.copy(upload = transform(it.upload)) } }
    }

    fun setActiveRemote(remoteId: String) {
        viewModelScope.launch { app.settings.setActiveRemote(remoteId) }
    }

    fun saveRemote(remote: Remote) {
        viewModelScope.launch { app.settings.upsertRemote(remote) }
    }

    fun deleteRemote(remote: Remote) {
        viewModelScope.launch { app.settings.removeRemote(remote.id) }
    }

    fun deleteAccount(account: AccountProfile) {
        viewModelScope.launch {
            app.settings.removeAccount(account.id)
            app.tokens.delete(account.id)
        }
    }

    fun hasToken(accountId: String): Boolean = app.tokens.has(accountId)

    fun setFormatEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            app.settings.update { current ->
                current.copy(formats = current.formats.map { if (it.id == id) it.copy(enabled = enabled) else it })
            }
        }
    }

    fun saveFormat(format: CopyFormat) {
        viewModelScope.launch {
            app.settings.update { current ->
                val index = current.formats.indexOfFirst { it.id == format.id }
                val formats = current.formats.toMutableList()
                if (index >= 0) formats[index] = format else formats += format
                current.copy(formats = formats)
            }
        }
    }

    fun deleteFormat(id: String) {
        viewModelScope.launch {
            app.settings.update { current -> current.copy(formats = current.formats.filterNot { it.id == id }) }
        }
    }

    fun resetFormats() {
        viewModelScope.launch {
            app.settings.update { current ->
                current.copy(
                    formats = CopyFormatDefaults.presets(),
                    activeFormatId = CopyFormatDefaults.MARKDOWN_ID,
                )
            }
        }
    }

    fun setActiveFormat(id: String) {
        viewModelScope.launch { app.settings.setActiveFormat(id) }
    }

    fun setTheme(choice: ThemeChoice) {
        viewModelScope.launch { app.settings.update { it.copy(theme = choice) } }
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        viewModelScope.launch {
            app.settings.update { current ->
                val next = if (enabled) {
                    current.enabledPlugins + pluginId
                } else {
                    current.enabledPlugins - pluginId
                }
                current.copy(enabledPlugins = GitHostPlugins.normaliseEnabled(next))
            }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { app.settings.update { it.copy(dynamicColor = enabled) } }
    }

    /** Branch names for the editor sheet; failures fall back to an empty list. */
    suspend fun branchesFor(remote: Remote): List<String> {
        val account = app.settings.current().accountOf(remote) ?: return emptyList()
        val token = app.tokens.read(account.id) ?: return emptyList()
        return runCatching {
            GitClients.create(account.provider, account.host).listBranches(token, remote)
        }.getOrDefault(emptyList())
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(container) as T
        }
    }
}