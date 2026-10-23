package com.example.aura.presentation.di

import com.example.aura.data.repository.SddmRepositoryImpl
import com.example.aura.domain.repository.SddmRepository
import com.example.aura.domain.usecase.ApplySddmBackgroundUseCase
import com.example.aura.domain.usecase.ChangeThemeUseCase
import com.example.aura.domain.usecase.RestoreBackupUseCase

/**
 * Composition root mínimo para la UI. Comparte una única implementación de
 * acceso a datos entre el repositorio de consulta y los casos de uso.
 */
object SddmModule {
    private val repositoryImpl: SddmRepositoryImpl by lazy { SddmRepositoryImpl() }

    val repository: SddmRepository get() = repositoryImpl

    val applyBackground: ApplySddmBackgroundUseCase by lazy {
        ApplySddmBackgroundUseCase(repositoryImpl)
    }

    val restoreBackup: RestoreBackupUseCase by lazy {
        RestoreBackupUseCase(repositoryImpl)
    }

    val changeTheme: ChangeThemeUseCase by lazy {
        ChangeThemeUseCase(repositoryImpl)
    }
}
