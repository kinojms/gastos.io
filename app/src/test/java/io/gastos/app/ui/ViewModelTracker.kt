package io.gastos.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Owns the ViewModels a test creates. [clear] cancels their viewModelScope (like leaving a screen)
 * and waits until every VM coroutine has finished. Tests call it in tearDown before
 * Dispatchers.resetMain(): with Main = Unconfined, VM coroutines resume on DataStore/Room threads and
 * would otherwise race the reset ("Dispatchers.Main is used concurrently with setting it").
 */
class ViewModelTracker {
    private val store = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private var created = 0

    fun <VM : ViewModel> track(cls: Class<VM>, make: () -> VM): VM {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = make() as T
        }
        return ViewModelProvider(store, factory)["vm${created++}", cls].also { jobs += it.viewModelScope.coroutineContext.job }
    }

    inline fun <reified VM : ViewModel> track(noinline make: () -> VM): VM = track(VM::class.java, make)

    fun clear() {
        store.clear()
        runBlocking { withTimeout(10_000) { jobs.forEach { it.join() } } }
    }
}
