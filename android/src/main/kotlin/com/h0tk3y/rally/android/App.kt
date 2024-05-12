package com.h0tk3y.rally.android

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.h0tk3y.rally.android.db.Database
import com.h0tk3y.rally.android.scenes.AllSectionsScene
import com.h0tk3y.rally.android.scenes.SectionScene
import com.h0tk3y.rally.db.Section
import moe.tlaster.precompose.navigation.NavHost
import moe.tlaster.precompose.navigation.NavOptions
import moe.tlaster.precompose.navigation.PopUpTo
import moe.tlaster.precompose.navigation.rememberNavigator
import moe.tlaster.precompose.navigation.transition.NavTransition

@Composable
fun App(
    database: Database,
    userPreferences: PreferenceRepository,
    applyTheme: @Composable (@Composable () -> Unit) -> Unit
) {
    val navigator = rememberNavigator()
    MaterialTheme(if (isSystemInDarkTheme()) darkColors() else lightColors()) {
        applyTheme {
            Surface {
                NavHost(
                    navigator = navigator, initialRoute = "/home",
                    navTransition = remember { navTransition() }
                ) {
                    scene("/home") {
                        val sections: LoadState<List<Section>> by database.selectAllSections()
                            .collectAsState(LoadState.LOADING)
                        AllSectionsScene(database, sections) { navigator.navigate("/section/${it.id}") }
                    }
                    scene("/section/{sectionId:[0-9]+}") { backStackEntry ->
                        val sectionId = backStackEntry.pathMap["sectionId"]?.toLong()
                            ?: throw IllegalStateException("expected sectionId in the route")
                        SectionScene(
                            sectionId,
                            database,
                            userPreferences,
                            onDeleteSection = {
                                database.deleteSectionById(sectionId)
                                navigator.goBack()
                            },
                            onNavigateToNewSection = { id ->
                                navigator.navigate("/section/$id", NavOptions(popUpTo = PopUpTo("/home")))
                            }
                        ) { navigator.goBack() }
                    }
                }
            }
        }
    }
}

private fun navTransition(): NavTransition {
    val fIn = fadeIn()
    val fOut = fadeOut()
    return NavTransition(
        createTransition = fIn,
        destroyTransition = fOut,
        pauseTransition = fOut,
        resumeTransition = fIn
    )
}