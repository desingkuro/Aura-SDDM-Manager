package com.example.aura.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aura.data.repository.MediaRepositoryImple
import com.example.aura.data.repository.SddmRepositoryImpl
import com.example.aura.domain.models.ApplyBackgroundResult
import com.example.aura.domain.models.BackgroundDestination
import com.example.aura.domain.repository.MediaRepository
import com.example.aura.domain.repository.SddmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ViewModel de la galería. Expone un único [MediaUiState] y recibe intenciones
 * desde la UI. Todo acceso a disco se ejecuta en [Dispatchers.IO].
 */
class MediaViewModel(
    private val mediaRepository: MediaRepository = MediaRepositoryImple(),
    private val sddmRepository: SddmRepository = SddmRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaUiState())
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    private var previewJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { loadSnapshot() }
            }.onSuccess { snapshot ->
                _uiState.update { current ->
                    val selected = current.selectedFile?.takeIf { it in snapshot.mediaFiles }
                    current.copy(
                        images = snapshot.images,
                        videos = snapshot.videos,
                        selectedFile = selected,
                        selectedType = if (selected == null) null else current.selectedType,
                        appliedBackground = snapshot.appliedBackground,
                        currentTheme = snapshot.currentTheme,
                        supportsSeparateScreens = snapshot.supportsSeparateScreens,
                        destination = if (snapshot.supportsSeparateScreens) {
                            current.destination
                        } else {
                            BackgroundDestination.BOTH
                        },
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No se pudo actualizar la galería: " +
                            (error.message ?: "error desconocido"),
                    )
                }
            }
        }
    }

    fun importFile(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    mediaRepository.createMediaDirectory(file)
                    loadSnapshot()
                }
            }.onSuccess { snapshot ->
                _uiState.update {
                    it.copy(
                        images = snapshot.images,
                        videos = snapshot.videos,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No se pudo importar \"${file.name}\". " +
                            "Detalle: ${error.message ?: "error desconocido"}",
                    )
                }
            }
        }
    }

    fun selectFile(file: File, type: String) {
        _uiState.update { it.copy(selectedFile = file, selectedType = type) }
    }

    fun openDeleteDialog() {
        if (_uiState.value.selectedFile == null) return
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    fun closeDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    fun deleteSelected() {
        val file = _uiState.value.selectedFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    mediaRepository.deletedFile(file)
                    loadSnapshot()
                }
            }.onSuccess { snapshot ->
                _uiState.update {
                    val selected = it.selectedFile?.takeIf { selectedFile ->
                        selectedFile in snapshot.mediaFiles
                    }
                    it.copy(
                        images = snapshot.images,
                        videos = snapshot.videos,
                        selectedFile = selected,
                        selectedType = if (selected == null) null else it.selectedType,
                        appliedBackground = snapshot.appliedBackground,
                        isLoading = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No se pudo eliminar \"${file.name}\". " +
                            "Detalle: ${error.message ?: "error desconocido"}",
                    )
                }
            }
        }
    }

    fun openApplyDialog() {
        val state = _uiState.value
        if (state.selectedFile == null || state.isApplying) return
        _uiState.update { it.copy(showApplyDialog = true) }
    }

    fun closeApplyDialog() {
        _uiState.update { it.copy(showApplyDialog = false) }
    }

    fun selectDestination(value: BackgroundDestination) {
        val state = _uiState.value
        if (!state.supportsSeparateScreens && value != BackgroundDestination.BOTH) return
        _uiState.update { it.copy(destination = value) }
    }

    fun launchSddmPreview() {
        val state = _uiState.value
        val file = state.selectedFile ?: return

        previewJob?.cancel()
        _uiState.update {
            it.copy(
                isPreviewing = true,
                previewMessage = null,
                previewSucceeded = true,
            )
        }
        previewJob = viewModelScope.launch {
            val result = runCatching {
                sddmRepository.launchPreview(file, state.destination)
            }
            _uiState.update {
                it.copy(
                    isPreviewing = false,
                    previewSucceeded = result.getOrNull() == null,
                    previewMessage = result.exceptionOrNull()?.let { error ->
                        "No se pudo abrir la previsualización: " +
                            (error.message ?: "error desconocido")
                    } ?: result.getOrNull(),
                )
            }
        }
    }

    fun clearPreviewMessage() {
        _uiState.update { it.copy(previewMessage = null) }
    }

    fun applySelectedBackground() {
        val state = _uiState.value
        val file = state.selectedFile ?: return
        if (state.isApplying) return

        val target = state.destination
        _uiState.update {
            it.copy(
                isApplying = true,
                applyMessage = null,
                applySucceeded = false,
            )
        }

        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    sddmRepository.applyBackground(file, target)
                }
            }

            val appliedBackground = runCatching {
                withContext(Dispatchers.IO) { sddmRepository.getAppliedBackground() }
            }.getOrNull()

            _uiState.update { current ->
                when (val applyResult = result.getOrNull()) {
                    is ApplyBackgroundResult.Success -> current.copy(
                        isApplying = false,
                        appliedBackground = appliedBackground,
                        applySucceeded = true,
                        applyMessage = "\"${applyResult.backgroundName}\" se aplicó correctamente a " +
                            "${target.targetDescription} del tema \"${applyResult.themeName}\".\n\n" +
                            "Se creó un respaldo automático que puedes restaurar desde Ajustes " +
                            "Avanzados.\n\nReinicia SDDM o cierra sesión para ver el cambio.",
                    )

                    is ApplyBackgroundResult.Failure -> current.copy(
                        isApplying = false,
                        appliedBackground = appliedBackground ?: current.appliedBackground,
                        applySucceeded = false,
                        applyMessage = applyResult.message,
                    )

                    null -> current.copy(
                        isApplying = false,
                        appliedBackground = appliedBackground ?: current.appliedBackground,
                        applySucceeded = false,
                        applyMessage = "No se pudo aplicar el fondo: " +
                            (result.exceptionOrNull()?.message ?: "error desconocido"),
                    )
                }
            }
        }
    }

    fun clearApplyMessage() {
        _uiState.update { it.copy(applyMessage = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        previewJob?.cancel()
        super.onCleared()
    }

    /** Cancela el trabajo pendiente cuando la pantalla sale de composición. */
    fun dispose() {
        previewJob?.cancel()
        viewModelScope.cancel()
    }

    private fun loadSnapshot(): MediaSnapshot {
        val images = mediaRepository.getImageFiles()
        val videos = mediaRepository.getVideoFiles()
        val applied = runCatching { sddmRepository.getAppliedBackground() }.getOrNull()
        val theme = runCatching { sddmRepository.getCurrentTheme() }.getOrDefault("")
        val supportsSeparate = runCatching { sddmRepository.supportsSeparateScreens() }
            .getOrDefault(true)

        return MediaSnapshot(
            images = images,
            videos = videos,
            appliedBackground = applied,
            currentTheme = theme,
            supportsSeparateScreens = supportsSeparate,
        )
    }

    private data class MediaSnapshot(
        val images: List<File>,
        val videos: List<File>,
        val appliedBackground: Pair<File, String>?,
        val currentTheme: String,
        val supportsSeparateScreens: Boolean,
    ) {
        val mediaFiles: List<File>
            get() = images + videos
    }
}
