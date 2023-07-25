package com.h0tk3y.rally.android.scenes

import android.content.res.Configuration
import android.graphics.Paint.Align
import androidx.appcompat.widget.LinearLayoutCompat.OrientationMode
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.db.Section
import kotlin.text.Typography.section

enum class DialogKind {
    CREATE, RENAME, DUPLICATE
}

@ExperimentalComposeUiApi
@Composable
fun CreateOrRenameSectionDialog(
    kind: DialogKind,
    existing: Section?,
    onDismiss: () -> Unit,
    onSave: (String) -> ItemSaveResult<Section>
) {
    var itemName by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var textFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text = itemName,
                selection = TextRange(itemName.length)
            )
        )
    }
    var isErrorEmptyName by rememberSaveable { mutableStateOf(false) }
    var itemAlreadyExists by rememberSaveable { mutableStateOf(false) }

    fun trySave() {
        if (itemName.isBlank()) {
            isErrorEmptyName = true
        } else {
            val result = onSave(itemName)
            itemAlreadyExists = result == ItemSaveResult.AlreadyExists
        }
    }

    val title = when (kind) {
        DialogKind.CREATE -> "Create section"
        DialogKind.DUPLICATE -> "Duplicate section"
        DialogKind.RENAME -> "Rename section"
    }

    DialogOnTop(onDismiss, title = title) {
        val focusRequester = remember { FocusRequester() }

        Column(Modifier.padding(8.dp)) {
            val textModifier = Modifier.fillMaxWidth()
            Column(textModifier) {
                OutlinedTextField(
                    value = textFieldValueState,
                    modifier = Modifier.focusRequester(focusRequester).fillMaxWidth(),
                    singleLine = true,
                    onValueChange = {
                        itemAlreadyExists = false
                        isErrorEmptyName = false
                        itemName = it.text
                        textFieldValueState = it
                    },
                    isError = itemAlreadyExists || isErrorEmptyName,
                    label = { Text("New section name") },
                )

                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                if (itemAlreadyExists) {
                    Box(Modifier.height(24.dp)) {
                        Text(text = "There is already a section with this name", color = MaterialTheme.colors.error)
                    }
                }
            }

            Row(
                modifier = textModifier,
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        trySave()
                    },
                    enabled = itemName.isNotBlank() && !itemAlreadyExists
                ) {
                    Text("Save")
                }
            }

        }
    }
}

sealed interface ItemSaveResult<out T> {
    data class Ok<T>(val createdItem: T) : ItemSaveResult<T>
    object AlreadyExists : ItemSaveResult<Nothing>
}