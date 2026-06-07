package org.zotero.android.screens.allitems.bottomsheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.PersistentList
import org.zotero.android.database.objects.Attachment
import org.zotero.android.uicomponents.modal.CustomModalBottomSheetM3

@Composable
internal fun AllItemsLinkedFileBottomSheet(
    attachments: PersistentList<Attachment>,
    showBottomSheet: Boolean,
    onAttachmentSelected: (Attachment) -> Unit,
    onClose: () -> Unit,
) {
    var shouldShow by remember { mutableStateOf(false) }
    LaunchedEffect(showBottomSheet) {
        if (showBottomSheet) shouldShow = true
    }

    if (shouldShow) {
        CustomModalBottomSheetM3(
            modifier = Modifier.windowInsetsPadding(BottomAppBarDefaults.windowInsets),
            shouldCollapse = !showBottomSheet,
            sheetContent = {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Open Attachment",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    HorizontalDivider()
                    attachments.forEach { attachment ->
                        val label = when (val type = attachment.type) {
                            is Attachment.Kind.file -> type.filename
                            is Attachment.Kind.url -> attachment.title
                        }
                        AllItemsAddBottomSheetRow(
                            title = label,
                            onClick = {
                                onClose()
                                onAttachmentSelected(attachment)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            },
            onCollapse = {
                shouldShow = false
                onClose()
            },
        )
    }
}
