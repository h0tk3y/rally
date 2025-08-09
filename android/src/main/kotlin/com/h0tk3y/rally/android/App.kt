package com.h0tk3y.rally.android

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import com.h0tk3y.rally.android.db.Database
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.android.racecervice.CommonRaceService
import com.h0tk3y.rally.android.racecervice.LocalRaceService
import com.h0tk3y.rally.android.racecervice.TcpStreamedRaceService
import com.h0tk3y.rally.android.scenes.AllSectionsScene
import com.h0tk3y.rally.android.scenes.PersistedSectionViewModel
import com.h0tk3y.rally.android.scenes.SectionEventLogScene
import com.h0tk3y.rally.android.scenes.SectionEventLogViewModel
import com.h0tk3y.rally.android.scenes.SectionOperations
import com.h0tk3y.rally.android.scenes.SectionScene
import com.h0tk3y.rally.android.scenes.SettingsScene
import com.h0tk3y.rally.android.scenes.SettingsViewModel
import com.h0tk3y.rally.android.scenes.StreamedSectionViewModel
import com.h0tk3y.rally.android.util.StreamingServerEmptyInfo
import com.h0tk3y.rally.db.Section
import kotlinx.serialization.Serializable

@Serializable
class HomeScene : NavKey

interface OnBackPopsUpTo {
    fun popsUpTo(navKey: NavKey): Boolean
}

@Serializable
data class SectionScene(val sectionId: Long, val withRace: Boolean, val popUpToHome: Boolean) : NavKey, OnBackPopsUpTo {
    override fun popsUpTo(navKey: NavKey): Boolean = popUpToHome && navKey is HomeScene
}

@Serializable
class StreamedSectionScene : NavKey

@Serializable
data class SectionEventLogScene(val sectionId: Long) : NavKey

@Serializable
data class SettingsScene(val currentDistance: Double?) : NavKey

private class NavigationModel(private val backStack: NavBackStack) {
    fun navigateTo(key: NavKey) {
        backStack.add(key)
    }
    
    private val backHandlers = mutableMapOf<NavKey, MutableList<() -> Unit>>()
    
    fun addOnBackHandler(key: NavKey, handler: () -> Unit) {
        backHandlers.getOrPut(key) { mutableListOf() }.add(handler)
    }

    fun removeOnBackHandler(key: NavKey, handler: () -> Unit) {
        backHandlers.get(key)?.remove(handler)
    }
    
    fun popBack(times: Int = 1) {
        fun popOne() {
            val popped = backStack.removeLastOrNull()
            backHandlers[popped]?.forEach { it() }
        }
        
        val current = backStack.lastOrNull()
        repeat(times) {
            popOne()
        }
        if (current is OnBackPopsUpTo) {
            val popUpTo = backStack.find { current.popsUpTo(it) }
            if (popUpTo != null) {
                while (backStack.lastOrNull() != popUpTo) {
                    popOne()
                }
            }
        }
    }
}

@Composable
fun App(
    database: Database,
    userPreferences: PreferenceRepository,
    applyTheme: @Composable (@Composable () -> Unit) -> Unit
) {
    val backStack = rememberNavBackStack(HomeScene())
    val navigation = remember { NavigationModel(backStack) }

    MaterialTheme(if (isSystemInDarkTheme()) darkColors() else lightColors()) {
        applyTheme {
            Surface {
                NavDisplay(
                    backStack,
                    onBack = navigation::popBack,
                    entryDecorators = listOf(
                        rememberSceneSetupNavEntryDecorator(),
                        rememberSavedStateNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator()
                    ),
                    entryProvider = entryProvider {
                        entry<HomeScene> {
                            val sections: LoadState<List<Section>> by database.selectAllSections()
                                .collectAsState(LoadState.LOADING)
                            AllSectionsScene(
                                database, sections,
                                onSelectSection = { navigation.navigateTo(SectionScene(it.id, false, false)) },
                                onGoToSettings = { navigation.navigateTo(SettingsScene(null)) },
                                onOpenDriverHud = { navigation.navigateTo(StreamedSectionScene()) }
                            )
                        }

                        entry<SectionScene> {
                            val sectionId = it.sectionId
                            val withRace = it.withRace

                            val context = LocalContext.current
                            val model = viewModel { PersistedSectionViewModel(sectionId, database, userPreferences) }
                            val connection = remember(context) {
                                localRaceServiceConnection(context, model::onServiceConnected, model::onServiceDisconnected)
                            }
                            model.setRaceServiceConnector { connectToService(context, LocalRaceService::class.java, connection) }
                            model.setRaceServiceDisconnector(connection::disconnectIfConnected)
                            DisposableEffect(context) { onDispose(connection::disconnectIfConnected) }

                            if (withRace) {
                                LaunchedEffect(sectionId) {
                                    model.enterRaceMode()
                                }
                            }

                            val sectionOps = object : SectionOperations {
                                override fun deleteThisSection() {
                                    database.deleteSectionById(sectionId)
                                    navigation.popBack()
                                }

                                override fun navigateToEventLog() {
                                    navigation.navigateTo(SectionEventLogScene(sectionId))
                                }

                                override fun renameThisSection(newName: String): SectionInsertOrRenameResult =
                                    database.renameSection(sectionId, newName)

                                override fun createSection(
                                    newName: String,
                                    serializedPositions: String
                                ): SectionInsertOrRenameResult =
                                    database.createSection(newName, serializedPositions)

                                override fun navigateToNewSection(sectionId: Long, isRace: Boolean) {
                                    val sectionSceneKey = SectionScene(sectionId, isRace, popUpToHome = true)
                                    navigation.navigateTo(sectionSceneKey)
                                }
                            }

                            SectionScene(
                                sectionOps,
                                onBack = navigation::popBack,
                                model = model,
                                emptySectionView = { Text("No section data") },
                                onGoToSettings = { navigation.navigateTo(SettingsScene(it)) },
                            )
                        }

                        entry<StreamedSectionScene> { key ->
                            val context = LocalContext.current
                            val model = viewModel { StreamedSectionViewModel() }

                            val connection = remember(context) {
                                tcpStreamedRaceServiceConnection(context, model::onServiceConnected, model::onServiceDisconnected)
                            }
                            val connector = { connectToService(context, TcpStreamedRaceService::class.java, connection) }
                            model.setRaceServiceConnector(connector)
                            model.setRaceServiceDisconnector(connection::disconnectIfConnected)

                            DisposableEffect(key) {
                                connector()
                                val navigationExitHandler = { 
                                    model.close()
                                }
                                navigation.addOnBackHandler(key, navigationExitHandler) 
                                onDispose {
                                    model.disconnectFromReceiver()
                                    connection.disconnectIfConnected()
                                    navigation.removeOnBackHandler(key, navigationExitHandler)
                                }
                            }

                            SectionScene(
                                sectionOps = null,
                                onBack = navigation::popBack,
                                model = model,
                                emptySectionView = { StreamingServerEmptyInfo() },
                                onGoToSettings = { navigation.navigateTo(SettingsScene(it)) },
                            )
                        }

                        entry<SectionEventLogScene> { key ->
                            val sectionId = key.sectionId
                            val model = viewModel { SectionEventLogViewModel(sectionId, database) }
                            SectionEventLogScene(model, onBack = navigation::popBack)
                        }

                        entry<SettingsScene> { key ->
                            val distance = key.currentDistance
                            SettingsScene(
                                onBack = navigation::popBack,
                                model = viewModel { SettingsViewModel(userPreferences) },
                                calibrateByCurrentDistance = distance
                            )
                        }
                    }
                )
            }
        }
    }
}

private fun <S : CommonRaceService> connectToService(
    context: Context,
    serviceClass: Class<S>,
    connection: RaceServiceConnection<S>
) {
    val intent = Intent(context, serviceClass)
    context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    ContextCompat.startForegroundService(context, intent)
}