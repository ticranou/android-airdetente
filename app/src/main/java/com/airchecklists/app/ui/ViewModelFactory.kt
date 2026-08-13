package com.airchecklists.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.airchecklists.app.data.repository.AircraftRepository
import com.airchecklists.app.di.ServiceLocator

/**
 * Generic factory that builds a ViewModel from the app's repository.
 * Lets screens do `viewModel(factory = repoViewModelFactory { MyVm(it) })`.
 */
inline fun <VM : ViewModel> repoViewModelFactory(
    crossinline create: (AircraftRepository) -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        create(ServiceLocator.repository) as T
}

/** Generic factory for ViewModels that build their own dependencies. */
inline fun <VM : ViewModel> simpleViewModelFactory(
    crossinline create: () -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
