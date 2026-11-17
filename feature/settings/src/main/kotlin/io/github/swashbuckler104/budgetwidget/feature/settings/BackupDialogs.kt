package io.github.swashbuckler104.budgetwidget.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/** Shortest password accepted for a protected backup (ADR-0044). */
internal const val MIN_BACKUP_PASSWORD = 8

/**
 * "Save a backup?" with an optional password (ADR-0044). Passwords are held in plain `remember`,
 * never `rememberSaveable`: saved state can be written to disk.
 */
@Composable
internal fun BackupDialog(onConfirm: (passphrase: String?) -> Unit, onDismiss: () -> Unit) {
    var protect by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = password.length < MIN_BACKUP_PASSWORD
    val mismatch = repeat.isNotEmpty() && repeat != password
    val ready = !protect || (!tooShort && repeat == password)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_export_confirm_title_backup)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = protect, role = Role.Checkbox) { protect = it },
                ) {
                    Checkbox(checked = protect, onCheckedChange = null)
                    Text(stringResource(R.string.settings_backup_protect), style = MaterialTheme.typography.bodyLarge)
                }
                if (protect) {
                    PasswordField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.settings_backup_password),
                        error = if (password.isNotEmpty() && tooShort) {
                            pluralStringResource(
                                R.plurals.settings_backup_password_short,
                                MIN_BACKUP_PASSWORD,
                                MIN_BACKUP_PASSWORD,
                            )
                        } else {
                            null
                        },
                        imeAction = ImeAction.Next,
                    )
                    PasswordField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = stringResource(R.string.settings_backup_password_repeat),
                        error = if (mismatch) stringResource(R.string.settings_backup_password_mismatch) else null,
                        imeAction = ImeAction.Done,
                    )
                    Text(
                        stringResource(R.string.settings_backup_protect_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        stringResource(R.string.settings_export_confirm_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = { onConfirm(password.takeIf { protect }) }) {
                Text(stringResource(R.string.settings_export_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

/** Asked after picking a password-protected backup to restore. [wrong]: the last try failed. */
@Composable
internal fun RestorePasswordDialog(wrong: Boolean, busy: Boolean, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_restore_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.settings_restore_password_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.settings_backup_password),
                    error = if (wrong) stringResource(R.string.settings_restore_password_wrong) else null,
                    imeAction = ImeAction.Done,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty() && !busy, onClick = { onConfirm(password) }) {
                Text(stringResource(R.string.settings_restore_password_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    imeAction: ImeAction,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        // Password keyboards don't learn or suggest what is typed.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
}
