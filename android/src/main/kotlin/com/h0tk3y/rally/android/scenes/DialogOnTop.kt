package com.h0tk3y.rally.android.scenes

import androidx.compose.foundation.layout.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun DialogOnTop(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable () -> Unit
) {
    AlertDialog(backgroundColor = MaterialTheme.colors.surface, modifier = Modifier.fillMaxWidth(), onDismissRequest = onDismiss, buttons = {
        Card {
            Column {
                Text(title, modifier = Modifier.padding(16.dp), fontSize = 18.sp)
                content()
            }
        }
    })
}