package com.h0tk3y.rally.android

import android.app.ComponentCaller
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.h0tk3y.rally.android.db.Database
import com.h0tk3y.rally.android.db.DatabaseDriverFactory
import com.h0tk3y.rally.android.theme.AppTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        super.onCreate(savedInstanceState)
        val database = Database(DatabaseDriverFactory(this))
        val repo = PreferenceRepository(dataStore)

        setContent {
            AppTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.surface)
                        .systemBarsPadding()
                        .imePadding()
                ) {
                    App(database, repo) {
                        val systemUiController = rememberSystemUiController()
                        systemUiController.setNavigationBarColor(
                            color = MaterialTheme.colors.primaryVariant
                        )
                        systemUiController.setSystemBarsColor(
                            color = MaterialTheme.colors.primaryVariant
                        )

                        it()
                    }
                }
            }
        }
    }
}