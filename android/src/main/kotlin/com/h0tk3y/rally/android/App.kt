package com.h0tk3y.rally.android

import android.content.Context
import android.content.Intent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import androidx.navigation.toRoute
import com.h0tk3y.rally.android.racecervice.RaceService
import com.h0tk3y.rally.android.db.Database
import com.h0tk3y.rally.android.scenes.AllSectionsScene
import com.h0tk3y.rally.android.scenes.SectionScene
import com.h0tk3y.rally.android.scenes.SectionViewModel
import com.h0tk3y.rally.db.Section
import kotlinx.serialization.Serializable


@Serializable
object HomeScene

@Serializable
data class SectionScene(val sectionId: Long, val withRace: Boolean)

@Composable
fun App(
    database: Database,
    userPreferences: PreferenceRepository,
    applyTheme: @Composable (@Composable () -> Unit) -> Unit
) {

    MaterialTheme(if (isSystemInDarkTheme()) darkColors() else lightColors()) {
        applyTheme {
            Surface {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = HomeScene,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                    popExitTransition = { fadeOut() },
                    popEnterTransition = { fadeIn() }
                ) {
                    composable<HomeScene> {
                        val sections: LoadState<List<Section>> by database.selectAllSections()
                            .collectAsState(LoadState.LOADING)
                        AllSectionsScene(database, sections) { navController.navigate(SectionScene(it.id, false)) }
                    }

                    composable<SectionScene> {
                        val sectionId = it.toRoute<SectionScene>().sectionId
                        val withRace = it.toRoute<SectionScene>().withRace

                        val context = LocalContext.current
                        val model = viewModel { SectionViewModel(sectionId, database, userPreferences) }
                        val connection = remember(context) {
                            RaceServiceConnection(context, model::onServiceConnected, model::onServiceDisconnected)
                        }

                        val connector: () -> Unit = {
                            val intent = Intent(context, RaceService::class.java)
                            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                            ContextCompat.startForegroundService(context, intent)
                        }
                        model.setRaceServiceConnector(connector)
                        model.setRaceServiceDisconnector { 
                            connection.disconnectIfConnected()
                        }

                        if (withRace) {
                            LaunchedEffect(sectionId) { 
                                model.enterRaceMode() 
                            }
                        }
                        
                        DisposableEffect(context) {
                            onDispose {
                                connection.disconnectIfConnected()
                            }
                        }
                        
                        SectionScene(
                            sectionId,
                            database,
                            onDeleteSection = {
                                database.deleteSectionById(sectionId)
                                navController.popBackStack()
                            },
                            onNavigateToNewSection = { id, isRace ->
                                navController.navigate(SectionScene(id, isRace), navOptions { popUpTo(HomeScene) })
                            },
                            onBack = {
                                navController.popBackStack()
                            },
                            model
                        )
                    }
                }
            }
        }
    }
}