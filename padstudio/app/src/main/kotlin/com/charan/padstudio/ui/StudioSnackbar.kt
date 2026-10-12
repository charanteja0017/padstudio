package com.charan.padstudio.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The shell's one snackbar host, for screens that report their own results with the time
 * ("Moved Notes to Work · 14:05", plan §13). One host means two messages never overlap: they queue.
 * Null outside MainActivity (the panels are separate activities), so callers fall back to inline text.
 *
 *     val snackbar = LocalStudioSnackbar.current
 *     scope.launch { snackbar?.showSnackbar("Moved Notes to Work · 14:05") }
 */
val LocalStudioSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }
