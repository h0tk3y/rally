package com.h0tk3y.rally.android.scenes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.h0tk3y.rally.android.db.Database
import com.h0tk3y.rally.android.LoadState
import com.h0tk3y.rally.android.db.SectionInsertOrRenameResult
import com.h0tk3y.rally.db.Section

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AllSectionsScene(
    database: Database,
    sections: LoadState<List<Section>>,
    onSelectSection: (Section) -> Unit
) {
    var showNewListDialog by rememberSaveable { mutableStateOf(false) }

    if (showNewListDialog) {
        CreateOrRenameSectionDialog(DialogKind.CREATE, existing = null, onDismiss = { showNewListDialog = false }, onSave = {
            when (val result = database.createEmptySection(it)) {
                is SectionInsertOrRenameResult.Success -> {
                    onSelectSection(result.section)
                    showNewListDialog = false
                    ItemSaveResult.Ok(result.section)
                }
                SectionInsertOrRenameResult.AlreadyExists -> ItemSaveResult.AlreadyExists
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("h0tk3y's Rally Tool") },
                actions = {
                    IconButton(onClick = { showNewListDialog = true }) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = "Create section")
                    }
                }
            )
        },
        content = { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (sections) {
                    is LoadState.Loaded -> {
                        SectionsListView(sections.value, onSelectSection)
                    }

                    LoadState.LOADING -> CenterTextBox("Loading sections...")
                    LoadState.EMPTY -> CenterTextBox("The section does not exist")
                    LoadState.FAILED -> CenterTextBox("Something went wrong")
                }
            }
        }
    )
}

@Composable
fun SectionsListView(
    sections: List<Section>,
    onListSelected: (Section) -> Unit
) {
    if (sections.isNotEmpty()) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(sections) { section ->
                Box(Modifier
                    .height(48.dp)
                    .fillMaxWidth()
                    .clickable { onListSelected(section) }
                ) {
                    Text(
                        section.name,
                        modifier = Modifier.padding(8.dp).align(Alignment.CenterStart)
                    )
                }
                Divider()
            }
        }
    } else {
        CenterTextBox("There is no sections yet, create one using the action bar button")
    }
}



@Composable fun CenterTextBox(text: String) {
    Box(Modifier.fillMaxSize()) {
        Text(
            text = text,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
