package com.davidegigante.spesesmart.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.davidegigante.spesesmart.ui.components.Tab
import com.davidegigante.spesesmart.ui.debug.DebugScreen
import com.davidegigante.spesesmart.ui.expenses.ExpenseEditorScreen
import com.davidegigante.spesesmart.ui.expenses.ExpensesScreen
import com.davidegigante.spesesmart.ui.fixed.FixedEditorScreen
import com.davidegigante.spesesmart.ui.fixed.FixedScreen
import com.davidegigante.spesesmart.ui.home.HomeScreen
import com.davidegigante.spesesmart.ui.settings.CategoriesScreen
import com.davidegigante.spesesmart.ui.settings.SettingsScreen

/** Le schermate dell'app. Le schede sono la base della pila, i dettagli ci vanno sopra. */
sealed interface Route {
    data class TabRoute(val tab: Tab) : Route
    /** id = null → nuova spesa. */
    data class EditExpense(val id: Long?) : Route
    data class EditFixed(val id: Long?) : Route
    data object Categories : Route
    data object Debug : Route
}

/**
 * Una schermata aperta. Ha i suoi ViewModel, che vengono buttati quando la schermata
 * si chiude: così "Nuova spesa" riparte sempre da un modulo vuoto.
 */
private class Entry(val route: Route, private val app: Application) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory =
        ViewModelProvider.AndroidViewModelFactory.getInstance(app)
    override val defaultViewModelCreationExtras: CreationExtras =
        MutableCreationExtras().apply { set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, app) }
}

/** Navigazione minima senza librerie: una pila di schermate e il tasto indietro. */
@Composable
fun AppRoot() {
    val app = LocalContext.current.applicationContext as Application
    val stack = remember { mutableStateListOf(Entry(Route.TabRoute(Tab.Home), app)) }

    fun push(route: Route) = stack.add(Entry(route, app))
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex).viewModelStore.clear()
    }
    fun selectTab(tab: Tab) {
        stack.forEach { it.viewModelStore.clear() }
        stack.clear()
        stack.add(Entry(Route.TabRoute(tab), app))
    }

    val top = stack.last()
    BackHandler(enabled = stack.size > 1 || top.route != Route.TabRoute(Tab.Home)) {
        if (stack.size > 1) pop() else selectTab(Tab.Home)
    }

    // key(top): ogni schermata aperta riparte con il suo stato, anche se è dello stesso tipo della precedente.
    CompositionLocalProvider(LocalViewModelStoreOwner provides top) { key(top) {
        when (val route = top.route) {
            is Route.TabRoute -> when (route.tab) {
                Tab.Home -> HomeScreen(
                    onTab = ::selectTab,
                    onAddExpense = { push(Route.EditExpense(null)) },
                    onOpenExpense = { push(Route.EditExpense(it)) },
                    onOpenFixed = { selectTab(Tab.Fixed) },
                    onOpenSettings = { selectTab(Tab.More) },
                )
                Tab.Expenses -> ExpensesScreen(
                    onTab = ::selectTab,
                    onAddExpense = { push(Route.EditExpense(null)) },
                    onOpenExpense = { push(Route.EditExpense(it)) },
                )
                Tab.Fixed -> FixedScreen(
                    onTab = ::selectTab,
                    onAdd = { push(Route.EditFixed(null)) },
                    onOpen = { push(Route.EditFixed(it)) },
                )
                Tab.More -> SettingsScreen(
                    onTab = ::selectTab,
                    onOpenCategories = { push(Route.Categories) },
                    onOpenDebug = { push(Route.Debug) },
                )
            }
            is Route.EditExpense -> ExpenseEditorScreen(expenseId = route.id, onDone = ::pop)
            is Route.EditFixed -> FixedEditorScreen(fixedId = route.id, onDone = ::pop)
            Route.Categories -> CategoriesScreen(onBack = ::pop)
            Route.Debug -> DebugScreen(onBack = ::pop)
        }
    } }
}
