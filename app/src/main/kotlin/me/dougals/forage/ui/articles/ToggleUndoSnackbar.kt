/*
 * Forage - an RSS reader for Android
 * Copyright (C) 2026 Dougie Richardson
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY;
 * see the LICENSE file for details.
 */
package me.dougals.forage.ui.articles

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import me.dougals.forage.R
import me.dougals.forage.presentation.articles.ToggleUndo

/** Shows a short "Marked read / Saved / …" snackbar with Undo for [toggled], and reports how it ended. */
@Composable
internal fun ToggleUndoSnackbar(toggled: ToggleUndo?, snackbar: SnackbarHostState, onUndo: () -> Unit, onDismiss: () -> Unit) {
    val undoLabel = stringResource(R.string.action_undo)
    val message = toggled?.let { toggle ->
        stringResource(
            when (toggle.field) {
                ToggleUndo.Field.READ -> if (toggle.value) R.string.toggled_read else R.string.toggled_unread
                ToggleUndo.Field.SAVED -> if (toggle.value) R.string.toggled_saved else R.string.toggled_unsaved
            },
        )
    }
    LaunchedEffect(toggled) {
        if (message == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) onUndo() else onDismiss()
    }
}
