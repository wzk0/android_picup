package com.example.picup.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.picup.AppContainer
import com.example.picup.data.AccountProfile
import com.example.picup.data.AppSettings
import com.example.picup.data.DEFAULT_PATH_TEMPLATE
import com.example.picup.data.Remote
import com.example.picup.data.RemoteUrls
import com.example.picup.git.GitClients
import com.example.picup.git.plugins.GitHostPlugins
import com.example.picup.git.RepoSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class SetupState(
    val step: Int = 0,
    /** Plugin id chosen for this wizard run. */
    val provider: String = GitHostPlugins.fallback.id,
    val host: String = "",
    val token: String = "",
    val tokenVisible: Boolean = false,
    val verifying: Boolean = false,
    val account: AccountProfile? = null,
    val login: String = "",
    val repos: List<RepoSummary> = emptyList(),
    val loadingRepos: Boolean = false,
    val repoQuery: String = "",
    val selectedRepo: RepoSummary? = null,
    val manualMode: Boolean = false,
    val manualOwner: String = "",
    val manualRepo: String = "",
    val branch: String = "",
    val branches: List<String> = emptyList(),
    val loadingBranches: Boolean = false,
    val pathTemplate: String = DEFAULT_PATH_TEMPLATE,
    val publicBase: String = "",
    val error: String? = null,
    val saving: Boolean = false,
)

/**
 * The three-step setup wizard: account → repository → where files land.
 *
 * Verification happens before anything is saved, so a mistyped token leaves no
 * half-configured repository behind.
 */
class SetupViewModel(private val app: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = app.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun setProvider(provider: String) {
        if (provider == _state.value.provider) return
        _state.update {
            it.copy(
                provider = provider,
                host = "",
                token = "",
                account = null,
                login = "",
                repos = emptyList(),
                selectedRepo = null,
                error = null,
                step = 0,
            )
        }
    }

    fun setHost(host: String) = _state.update { it.copy(host = host, error = null) }

    fun setToken(token: String) = _state.update { it.copy(token = token, error = null) }

    fun toggleTokenVisible() = _state.update { it.copy(tokenVisible = !it.tokenVisible) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun signIn() {
        val state = _state.value
        if (state.verifying) return
        val token = state.token.trim()
        if (token.isBlank()) {
            _state.update { it.copy(error = "请先粘贴访问令牌") }
            return
        }
        val plugin = GitHostPlugins.byId(state.provider)
        if (plugin.requiresHost && RemoteUrls.cleanHost(state.host).isBlank()) {
            _state.update { it.copy(error = "${plugin.displayName} 需要填写实例地址，例如 git.example.com") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(verifying = true, error = null) }
            try {
                val client = GitClients.create(state.provider, state.host)
                val info = client.verify(token)
                val account = AccountProfile(
                    id = UUID.randomUUID().toString(),
                    provider = state.provider,
                    host = RemoteUrls.cleanHost(state.host),
                    login = info.login,
                )
                _state.update {
                    it.copy(
                        verifying = false,
                        account = account,
                        login = info.login,
                        token = token,
                        step = 1,
                        error = null,
                    )
                }
                loadRepos(account, token)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update { it.copy(verifying = false, error = error.message ?: "登录失败") }
            }
        }
    }

    fun useExistingAccount(account: AccountProfile) {
        val token = app.tokens.read(account.id)
        if (token.isNullOrBlank()) {
            _state.update { it.copy(error = "这个账号的 Token 已失效，请重新粘贴一次") }
            return
        }
        _state.update {
            it.copy(
                provider = account.provider,
                host = account.host,
                token = token,
                account = account,
                login = account.login,
                step = 1,
                error = null,
                repos = emptyList(),
            )
        }
        loadRepos(account, token)
    }

    fun setRepoQuery(query: String) = _state.update { it.copy(repoQuery = query) }

    fun selectRepo(repo: RepoSummary) {
        val account = _state.value.account ?: return
        val token = _state.value.token
        _state.update {
            it.copy(
                selectedRepo = repo,
                branch = repo.defaultBranch.ifBlank { "main" },
                branches = emptyList(),
                manualMode = false,
                error = null,
            )
        }
        loadBranches(account, token, repo)
    }

    fun setManualMode(enabled: Boolean) = _state.update {
        it.copy(manualMode = enabled, selectedRepo = if (enabled) null else it.selectedRepo, error = null)
    }

    fun setManualOwner(owner: String) = _state.update { it.copy(manualOwner = owner, error = null) }

    fun setManualRepo(repo: String) = _state.update { it.copy(manualRepo = repo, error = null) }

    fun applyManualRepo() {
        val state = _state.value
        val account = state.account ?: return
        val owner = state.manualOwner.trim().trim('/')
        val repo = state.manualRepo.trim().trim('/')
        if (owner.isBlank() || repo.isBlank()) {
            _state.update { it.copy(error = "请填写 owner 和仓库名") }
            return
        }
        val summary = RepoSummary(owner = owner, name = repo, defaultBranch = state.branch.ifBlank { "main" })
        _state.update {
            it.copy(
                selectedRepo = summary,
                branch = summary.defaultBranch,
                branches = emptyList(),
                error = null,
            )
        }
        loadBranches(account, state.token, summary)
    }

    fun setBranch(branch: String) = _state.update { it.copy(branch = branch, error = null) }

    fun setPathTemplate(template: String) = _state.update { it.copy(pathTemplate = template) }

    fun setPublicBase(base: String) = _state.update { it.copy(publicBase = base) }

    fun useJsdelivr() {
        val state = _state.value
        val repo = state.selectedRepo ?: return
        val branch = state.branch.ifBlank { repo.defaultBranch }
        _state.update {
            it.copy(publicBase = RemoteUrls.jsdelivrBase(repo.owner, repo.name, branch))
        }
    }

    /** Returns false when step 0 is already showing, so the screen can pop. */
    fun back(): Boolean {
        val current = _state.value.step
        if (current == 0) return false
        _state.update { it.copy(step = current - 1, error = null) }
        return true
    }

    fun next() {
        val state = _state.value
        when (state.step) {
            0 -> signIn()
            1 -> {
                if (state.selectedRepo == null) {
                    _state.update { it.copy(error = "请选择或手动填写一个仓库") }
                } else {
                    _state.update { it.copy(step = 2, error = null) }
                }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _state.value
        if (state.saving) return
        val account = state.account
        val repo = state.selectedRepo
        val token = state.token.trim()
        if (account == null || repo == null) {
            _state.update { it.copy(error = "账号或仓库还没有就绪") }
            return
        }
        if (token.isBlank()) {
            _state.update { it.copy(error = "Token 为空，请返回上一步重新粘贴") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val current = app.settings.current()
                val existing = current.accounts.firstOrNull {
                    it.provider == account.provider &&
                        RemoteUrls.cleanHost(it.host) == RemoteUrls.cleanHost(account.host) &&
                        it.login == account.login
                }
                val accountId = existing?.id ?: account.id
                app.tokens.write(accountId, token)
                app.settings.upsertAccount(account.copy(id = accountId))
                val remote = Remote(
                    id = UUID.randomUUID().toString(),
                    accountId = accountId,
                    owner = repo.owner,
                    repo = repo.name,
                    branch = state.branch.trim().ifBlank { repo.defaultBranch.ifBlank { "main" } },
                    pathTemplate = state.pathTemplate.trim(),
                    publicBase = state.publicBase.trim(),
                )
                app.settings.upsertRemote(remote, makeActive = true)
                if (state.pathTemplate.isNotBlank()) {
                    app.settings.update { settings ->
                        settings.copy(
                            upload = settings.upload.copy(pathTemplate = state.pathTemplate.trim()),
                        )
                    }
                }
                _state.update { it.copy(saving = false) }
                onSaved()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update { it.copy(saving = false, error = error.message ?: "保存失败") }
            }
        }
    }

    private fun loadRepos(account: AccountProfile, token: String) {
        viewModelScope.launch {
            _state.update { it.copy(loadingRepos = true, error = null) }
            try {
                val repos = GitClients.create(account.provider, account.host).listRepos(token)
                _state.update {
                    it.copy(
                        loadingRepos = false,
                        repos = repos,
                        error = if (repos.isEmpty()) "这个账号没有可写入的仓库，可以手动填写一个再试" else null,
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                _state.update { it.copy(loadingRepos = false, error = error.message ?: "读取仓库失败") }
            }
        }
    }

    private fun loadBranches(account: AccountProfile, token: String, repo: RepoSummary) {
        viewModelScope.launch {
            _state.update { it.copy(loadingBranches = true) }
            val remote = Remote(
                id = "probe",
                accountId = account.id,
                owner = repo.owner,
                repo = repo.name,
                branch = repo.defaultBranch.ifBlank { "main" },
            )
            val branches = runCatching {
                GitClients.create(account.provider, account.host).listBranches(token, remote)
            }.getOrDefault(emptyList())
            _state.update { it.copy(loadingBranches = false, branches = branches) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SetupViewModel(container) as T
        }
    }
}