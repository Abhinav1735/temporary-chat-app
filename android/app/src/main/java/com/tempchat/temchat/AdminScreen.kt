
package com.tempchat.temchat

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tempchat.temchat.data.remote.AdminApi
import com.tempchat.temchat.data.remote.AdminUser

@Composable
fun AdminScreen(
    token: String,
    currentUser: String,
    onLogout: () -> Unit
) {
    val handler = remember { Handler(Looper.getMainLooper()) }

    var users by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resetPassword by remember { mutableStateOf("") }

    // Independent visibility controls for both password fields.
    var createPasswordVisible by remember { mutableStateOf(false) }
    var resetPasswordVisible by remember { mutableStateOf(false) }

    var showCreateForm by remember { mutableStateOf(false) }
    var resetUser by remember { mutableStateOf<AdminUser?>(null) }
    var deleteUser by remember { mutableStateOf<AdminUser?>(null) }

    var loading by remember { mutableStateOf(false) }
    var busyUserId by remember { mutableStateOf<Long?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }

    fun loadUsers() {
        loading = true
        message = null

        AdminApi.getUsers(
            token = token,
            onSuccess = { result ->
                handler.post {
                    users = result
                    loading = false
                }
            },
            onError = { reason ->
                handler.post {
                    loading = false
                    message = reason
                    error = true
                }
            }
        )
    }

    LaunchedEffect(token) {
        loadUsers()
    }

    val visibleUsers = users.filter {
        it.loginId.contains(search.trim(), ignoreCase = true)
    }

    val enabledCount = users.count { it.enabled }
    val disabledCount = users.size - enabledCount

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "TempChat Admin",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Signed in as $currentUser",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                TextButton(onClick = onLogout) {
                    Text("Logout")
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    "Total",
                    users.size.toString(),
                    Modifier.weight(1f)
                )
                SummaryCard(
                    "Enabled",
                    enabledCount.toString(),
                    Modifier.weight(1f)
                )
                SummaryCard(
                    "Disabled",
                    disabledCount.toString(),
                    Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        showCreateForm = !showCreateForm
                        message = null

                        if (!showCreateForm) {
                            password = ""
                            createPasswordVisible = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (showCreateForm) "Close form" else "Create user")
                }

                TextButton(
                    onClick = { loadUsers() },
                    enabled = !loading
                ) {
                    Text("Refresh")
                }
            }

            if (showCreateForm) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Create a user",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = loginId,
                            onValueChange = { loginId = it },
                            label = { Text("Login ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Temporary password") },
                            singleLine = true,
                            visualTransformation = if (createPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                TextButton(
                                    onClick = {
                                        createPasswordVisible =
                                            !createPasswordVisible
                                    }
                                ) {
                                    Text(
                                        if (createPasswordVisible) "Hide"
                                        else "Show"
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val cleanId = loginId.trim()

                                when {
                                    cleanId.length !in 4..50 -> {
                                        message =
                                            "Login ID must be 4–50 characters."
                                        error = true
                                    }

                                    password.length !in 8..100 -> {
                                        message =
                                            "Password must be 8–100 characters."
                                        error = true
                                    }

                                    else -> {
                                        loading = true
                                        message = null

                                        AdminApi.createUser(
                                            token = token,
                                            loginId = cleanId,
                                            password = password,
                                            onSuccess = {
                                                handler.post {
                                                    loading = false
                                                    loginId = ""
                                                    password = ""
                                                    createPasswordVisible = false
                                                    showCreateForm = false
                                                    message =
                                                        "User created. They must change their temporary credentials at first login."
                                                    error = false
                                                    loadUsers()
                                                }
                                            },
                                            onError = { reason ->
                                                handler.post {
                                                    loading = false
                                                    message = reason
                                                    error = true
                                                }
                                            }
                                        )
                                    }
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Create account")
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search users by Login ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    color = if (error) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            when {
                loading && users.isEmpty() -> {
                    CircularProgressIndicator()
                }

                visibleUsers.isEmpty() -> {
                    Text(
                        if (search.isBlank()) {
                            "No users found."
                        } else {
                            "No users match your search."
                        }
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleUsers, key = { it.id }) { user ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.SpaceBetween
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                user.loginId,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                "ID: ${user.id} • Role: ${user.role}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Text(
                                                if (user.enabled) "Enabled"
                                                else "Disabled",
                                                color = if (user.enabled) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                }
                                            )

                                            if (user.mustChangeCredentials) {
                                                Text(
                                                    "Password change required",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }

                                        Switch(
                                            checked = user.enabled,
                                            enabled = busyUserId != user.id &&
                                                    user.role != "ADMIN",
                                            onCheckedChange = { enabled ->
                                                busyUserId = user.id
                                                message = null

                                                AdminApi.updateStatus(
                                                    token = token,
                                                    userId = user.id,
                                                    enabled = enabled,
                                                    onSuccess = { updated ->
                                                        handler.post {
                                                            users = users.map {
                                                                if (it.id == updated.id) {
                                                                    updated
                                                                } else {
                                                                    it
                                                                }
                                                            }
                                                            busyUserId = null
                                                            message =
                                                                "Account status updated."
                                                            error = false
                                                        }
                                                    },
                                                    onError = { reason ->
                                                        handler.post {
                                                            busyUserId = null
                                                            message = reason
                                                            error = true
                                                        }
                                                    }
                                                )
                                            }
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.spacedBy(6.dp)
                                    ) {
                                        TextButton(
                                            enabled = busyUserId == null &&
                                                    user.role != "ADMIN",
                                            onClick = {
                                                resetUser = user
                                                resetPassword = ""
                                                resetPasswordVisible = false
                                            }
                                        ) {
                                            Text("Reset password")
                                        }

                                        TextButton(
                                            enabled = busyUserId == null &&
                                                    user.role != "ADMIN",
                                            onClick = { deleteUser = user }
                                        ) {
                                            Text(
                                                "Delete",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    resetUser?.let { target ->
        AlertDialog(
            onDismissRequest = {
                resetUser = null
                resetPassword = ""
                resetPasswordVisible = false
            },
            title = { Text("Reset password") },
            text = {
                Column {
                    Text(
                        "Set a temporary password for ${target.loginId}. " +
                                "They must change it at next login."
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = resetPassword,
                        onValueChange = { resetPassword = it },
                        label = { Text("New temporary password") },
                        singleLine = true,
                        visualTransformation = if (resetPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(
                                onClick = {
                                    resetPasswordVisible =
                                        !resetPasswordVisible
                                }
                            ) {
                                Text(
                                    if (resetPasswordVisible) "Hide"
                                    else "Show"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = busyUserId == null &&
                            resetPassword.length in 8..100,
                    onClick = {
                        busyUserId = target.id

                        AdminApi.resetPassword(
                            token = token,
                            userId = target.id,
                            newPassword = resetPassword,
                            onSuccess = { updated ->
                                handler.post {
                                    users = users.map {
                                        if (it.id == updated.id) {
                                            updated
                                        } else {
                                            it
                                        }
                                    }
                                    busyUserId = null
                                    resetUser = null
                                    resetPassword = ""
                                    resetPasswordVisible = false
                                    message =
                                        "Temporary password reset successfully."
                                    error = false
                                }
                            },
                            onError = { reason ->
                                handler.post {
                                    busyUserId = null
                                    message = reason
                                    error = true
                                }
                            }
                        )
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        resetUser = null
                        resetPassword = ""
                        resetPasswordVisible = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    deleteUser?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteUser = null },
            title = { Text("Delete user?") },
            text = {
                Text(
                    "Permanently delete ${target.loginId}? " +
                            "This action cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = busyUserId == null,
                    onClick = {
                        busyUserId = target.id

                        AdminApi.deleteUser(
                            token = token,
                            userId = target.id,
                            onSuccess = {
                                handler.post {
                                    users = users.filterNot {
                                        it.id == target.id
                                    }
                                    busyUserId = null
                                    deleteUser = null
                                    message = "User deleted."
                                    error = false
                                }
                            },
                            onError = { reason ->
                                handler.post {
                                    busyUserId = null
                                    message = reason
                                    error = true
                                }
                            }
                        )
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
