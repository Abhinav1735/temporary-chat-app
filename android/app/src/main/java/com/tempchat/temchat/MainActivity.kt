
package com.tempchat.temchat

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tempchat.temchat.data.LoginRequest
import com.tempchat.temchat.data.LoginResponse
import com.tempchat.temchat.data.remote.AuthApi
import com.tempchat.temchat.data.remote.ChatUser
import com.tempchat.temchat.data.remote.ChatUsersApi
import com.tempchat.temchat.data.remote.ChatWebSocket
import com.tempchat.temchat.ui.theme.TemchatTheme
import org.json.JSONObject

data class ChatUiMessage(
    val sender: String,
    val receiver: String,
    val message: String,
    val timestamp: String
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TemchatTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TempChatApp(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        ChatWebSocket.disconnect()
        super.onDestroy()
    }
}


@Composable
fun TempChatApp(
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val preferences = remember {
        context.getSharedPreferences(
            "tempchat_session",
            Context.MODE_PRIVATE
        )
    }

    var currentUser by remember { mutableStateOf("") }
    var currentRole by remember { mutableStateOf("USER") }
    var isLoggedIn by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Disconnected") }
    var connectionError by remember { mutableStateOf<String?>(null) }

    var pendingCredentials by remember {
        mutableStateOf<LoginResponse?>(null)
    }

    var temporaryPassword by remember {
        mutableStateOf("")
    }

    val messages = remember {
        mutableStateListOf<ChatUiMessage>()
    }

    var chatUsers by remember {
        mutableStateOf<List<ChatUser>>(emptyList())
    }

    var onlineUsers by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    var isLoadingUsers by remember {
        mutableStateOf(false)
    }

    var selectedUser by remember {
        mutableStateOf<String?>(null)
    }

    val mainHandler = remember {
        Handler(Looper.getMainLooper())
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    val loadChatUsers: (String) -> Unit = { token ->
        isLoadingUsers = true

        ChatUsersApi.fetchUsers(
            token = token,
            onSuccess = { users ->
                mainHandler.post {
                    chatUsers = users
                    isLoadingUsers = false

                    if (selectedUser != null &&
                        users.none { it.loginId == selectedUser }
                    ) {
                        selectedUser = null
                    }
                }
            },
            onError = { error ->
                mainHandler.post {
                    isLoadingUsers = false
                    connectionError = error
                    Log.e("TempChat", "Could not load users: $error")
                }
            }
        )
    }

    val connectToChat: (String, String) -> Unit = { user, token ->
        currentUser = user
        connectionStatus = "Connecting..."
        connectionError = null

        ChatWebSocket.connect(
            token = token,
            onConnected = {
                mainHandler.post {
                    currentUser = user
                    connectionStatus = "Connected"
                    connectionError = null
                    isLoggedIn = true
                }
            },
            onMessage = { rawMessage ->
                mainHandler.post {
                    try {
                        val json = JSONObject(rawMessage)
                        val eventType = json.optString("type")

                        if (eventType == "presence" ||
                            eventType == "presence_snapshot"
                        ) {
                            when (eventType) {
                                "presence" -> {
                                    val loginId = json.optString("loginId")
                                    val online = json.optBoolean("online", false)

                                    onlineUsers =
                                        if (online) {
                                            onlineUsers + loginId
                                        } else {
                                            onlineUsers - loginId
                                        }
                                }

                                "presence_snapshot" -> {
                                    val usersArray = json.optJSONArray("users")
                                    val currentOnline = mutableSetOf<String>()

                                    if (usersArray != null) {
                                        for (i in 0 until usersArray.length()) {
                                            currentOnline.add(
                                                usersArray.optString(i)
                                            )
                                        }
                                    }

                                    onlineUsers = currentOnline
                                }
                            }
                        } else {
                            val incoming = ChatUiMessage(
                                sender = json.optString("sender"),
                                receiver = json.optString("receiver"),
                                message = json.optString("message"),
                                timestamp = json.optString("timestamp")
                            )

                            if (incoming.message.isNotBlank()) {
                                messages.add(incoming)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(
                            "TempChat",
                            "Could not parse WebSocket message",
                            e
                        )
                    }
                }
            },
            onError = { error ->
                mainHandler.post {
                    connectionStatus = "Connection error"
                    connectionError = error
                    Log.e("TempChat", error)
                }
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START &&
                pendingCredentials == null
            ) {
                val savedUser = preferences.getString("login_id", null)
                val savedToken = preferences.getString("token", null)
                val savedRole = preferences.getString("role", "USER") ?: "USER"

                if (!savedUser.isNullOrBlank() &&
                    !savedToken.isNullOrBlank() &&
                    !isLoggedIn
                ) {
                    currentRole = savedRole.uppercase()
                    connectToChat(savedUser, savedToken)

                    // Only load chat contacts for regular users.
                    if (currentRole != "ADMIN") {
                        loadChatUsers(savedToken)
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    when {
        pendingCredentials != null -> {
            val pending = pendingCredentials!!

            ChangeCredentialsScreen(
                currentLoginId = pending.loginId,
                token = pending.token,
                currentPassword = temporaryPassword,
                onCredentialsChanged = { response ->
                    if (response.mustChangeCredentials) {
                        connectionError =
                            "The server still requires a credential change."
                    } else {
                        currentRole = response.role.uppercase()

                        preferences.edit()
                            .putString("login_id", response.loginId)
                            .putString("token", response.token)
                            .putString("role", currentRole)
                            .apply()

                        temporaryPassword = ""
                        pendingCredentials = null
                        connectionError = null

                        connectToChat(
                            response.loginId,
                            response.token
                        )

                        if (currentRole != "ADMIN") {
                            loadChatUsers(response.token)
                        }
                    }
                }
            )
        }

        !isLoggedIn -> {
            LoginScreen(
                modifier = modifier,
                onLoginSuccess = { response, password ->
                    if (response.mustChangeCredentials) {
                        ChatWebSocket.disconnect()
                        preferences.edit().clear().apply()

                        temporaryPassword = password
                        pendingCredentials = response
                    } else {
                        currentRole = response.role.uppercase()

                        preferences.edit()
                            .putString("login_id", response.loginId)
                            .putString("token", response.token)
                            .putString("role", currentRole)
                            .apply()

                        connectToChat(
                            response.loginId,
                            response.token
                        )

                        if (currentRole != "ADMIN") {
                            loadChatUsers(response.token)
                        }
                    }
                }
            )
        }

        else -> {
            if (currentRole == "ADMIN") {
                AdminScreen(
                    token = preferences.getString("token", "").orEmpty(),
                    currentUser = currentUser,
                    onLogout = {
                        ChatWebSocket.disconnect()
                        messages.clear()
                        chatUsers = emptyList()
                        onlineUsers = emptySet()
                        selectedUser = null

                        preferences.edit().clear().apply()

                        currentUser = ""
                        currentRole = "USER"
                        isLoggedIn = false
                        pendingCredentials = null
                        temporaryPassword = ""
                        connectionStatus = "Disconnected"
                        connectionError = null
                        isLoadingUsers = false
                    }
                )
            } else {
                ChatScreen(
                    modifier = modifier,
                    currentUser = currentUser,
                    messages = messages,
                    connectionStatus = connectionStatus,
                    connectionError = connectionError,
                    chatUsers = chatUsers,
                    onlineUsers = onlineUsers,
                    isLoadingUsers = isLoadingUsers,
                    selectedUser = selectedUser,
                    sessionToken = preferences.getString("token", "").orEmpty(),
                    mainHandler = mainHandler,
                    onRefreshContacts = {
                        val token =
                            preferences.getString("token", "").orEmpty()

                        if (token.isNotBlank()) {
                            loadChatUsers(token)
                        }
                    },
                    onUserSelected = { user ->
                        selectedUser = user
                    },
                    onSendMessage = { receiver, message ->
                        val sent = ChatWebSocket.sendMessage(
                            receiver = receiver,
                            message = message
                        )

                        if (!sent) {
                            connectionError =
                                "Message could not be sent. Check the connection."
                        } else {
                            connectionError = null
                        }

                        sent
                    },
                    onLogout = {
                        ChatWebSocket.disconnect()
                        messages.clear()
                        chatUsers = emptyList()
                        onlineUsers = emptySet()
                        selectedUser = null

                        preferences.edit().clear().apply()

                        currentUser = ""
                        currentRole = "USER"
                        isLoggedIn = false
                        pendingCredentials = null
                        temporaryPassword = ""
                        connectionStatus = "Disconnected"
                        connectionError = null
                        isLoadingUsers = false
                    }
                )
            }
        }
    }
}


@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onLoginSuccess: (LoginResponse, String) -> Unit
) {
    var loginId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "TempChat",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(Modifier.height(8.dp))
        Text("Login to continue")
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = loginId,
            onValueChange = {
                loginId = it
                errorMessage = null
            },
            label = { Text("Login ID") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Password") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(
                    onClick = {
                        passwordVisible = !passwordVisible
                    }
                ) {
                    Text(if (passwordVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (loginId.isBlank() || password.isBlank()) {
                    errorMessage = "Please enter Login ID and password"
                    return@Button
                }

                isLoading = true
                errorMessage = null

                AuthApi.login(
                    loginRequest = LoginRequest(
                        loginId = loginId.trim(),
                        password = password
                    ),
                    onSuccess = { response ->
                        isLoading = false
                        onLoginSuccess(response, password)
                    },
                    onError = { error ->
                        isLoading = false
                        errorMessage = error
                    }
                )
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("Login")
            }
        }

        Spacer(Modifier.height(16.dp))

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun ChangeCredentialsScreen(
    currentLoginId: String,
    token: String,
    currentPassword: String,
    onCredentialsChanged: (LoginResponse) -> Unit
) {
    var newLoginId by remember(currentLoginId) {
        mutableStateOf(currentLoginId)
    }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Secure Your Account",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Change your temporary login ID and password to continue.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = newLoginId,
            onValueChange = {
                newLoginId = it
                errorMessage = null
            },
            label = { Text("New Login ID") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = newPassword,
            onValueChange = {
                newPassword = it
                errorMessage = null
            },
            label = { Text("New Password") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = if (newPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(
                    onClick = {
                        newPasswordVisible = !newPasswordVisible
                    }
                ) {
                    Text(if (newPasswordVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {
                confirmPassword = it
                errorMessage = null
            },
            label = { Text("Confirm New Password") },
            singleLine = true,
            enabled = !isLoading,
            visualTransformation = if (confirmPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(
                    onClick = {
                        confirmPasswordVisible = !confirmPasswordVisible
                    }
                ) {
                    Text(if (confirmPasswordVisible) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Login ID: 4–50 characters. Password: at least 8 characters.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                when {
                    newLoginId.trim().length < 4 ||
                            newLoginId.trim().length > 50 -> {
                        errorMessage =
                            "Login ID must contain 4–50 characters."
                    }

                    newPassword.length < 8 -> {
                        errorMessage =
                            "Password must contain at least 8 characters."
                    }

                    newPassword != confirmPassword -> {
                        errorMessage = "Passwords do not match."
                    }

                    else -> {
                        isLoading = true
                        errorMessage = null

                        AuthApi.changeCredentials(
                            token = token,
                            currentPassword = currentPassword,
                            newLoginId = newLoginId.trim(),
                            newPassword = newPassword,
                            onSuccess = { response ->
                                isLoading = false

                                // Clear password fields immediately.
                                newPassword = ""
                                confirmPassword = ""

                                onCredentialsChanged(response)
                            },
                            onError = { error ->
                                isLoading = false
                                errorMessage = error
                            }
                        )
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text("Save & Continue")
            }
        }

        errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    currentUser: String,
    messages: List<ChatUiMessage>,
    connectionStatus: String,
    connectionError: String?,
    chatUsers: List<ChatUser>,
    onlineUsers: Set<String>,
    isLoadingUsers: Boolean,
    selectedUser: String?,
    sessionToken: String,
    mainHandler: Handler,
    onRefreshContacts: () -> Unit,
    onUserSelected: (String) -> Unit,
    onSendMessage: (String, String) -> Boolean,
    onLogout: () -> Unit
) {
    var showConversation by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var sendError by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ChatUser>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var addingContactId by remember { mutableStateOf<Long?>(null) }

    val receiver = selectedUser.orEmpty()

    val conversation = messages.filter {
        (it.sender == currentUser && it.receiver == receiver) ||
                (it.sender == receiver && it.receiver == currentUser)
    }

    if (!showConversation) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TempChat",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Logged in as $currentUser",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(onClick = onLogout) {
                    Text(
                        "Logout",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chats",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connectionStatus == "Connected") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            connectionError?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Search for an existing TempChat account and add it to Chats.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Find people",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        searchMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search by Login ID") },
                    singleLine = true,
                    enabled = !isSearching
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val query = searchQuery.trim()
                        if (query.isBlank()) {
                            searchMessage = "Enter a Login ID to search."
                        } else if (sessionToken.isBlank()) {
                            searchMessage = "Your session has expired. Please log in again."
                        } else {
                            isSearching = true
                            searchMessage = null
                            searchResults = emptyList()

                            ChatUsersApi.searchUsers(
                                token = sessionToken,
                                query = query,
                                onSuccess = { users ->
                                    mainHandler.post {
                                        isSearching = false
                                        searchResults = users
                                        searchMessage = if (users.isEmpty()) {
                                            "User not found"
                                        } else {
                                            null
                                        }
                                    }
                                },
                                onError = { error ->
                                    mainHandler.post {
                                        isSearching = false
                                        searchResults = emptyList()
                                        searchMessage = error
                                    }
                                }
                            )
                        }
                    },
                    enabled = !isSearching,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Searching...")
                    } else {
                        Text("Search users")
                    }
                }

                searchMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 6.dp),
                        color = if (
                            message == "User not found" ||
                            message.endsWith("added to Chats.")
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (searchResults.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    searchResults.forEach { result ->
                        val alreadyAdded = chatUsers.any {
                            it.id == result.id || it.loginId.equals(
                                result.loginId,
                                ignoreCase = true
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.loginId,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = if (alreadyAdded) {
                                        "Already added to Chats"
                                    } else {
                                        "TempChat user"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (alreadyAdded) {
                                Text(
                                    text = "Added",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Button(
                                    onClick = {
                                        if (sessionToken.isNotBlank()) {
                                            addingContactId = result.id
                                            searchMessage = null

                                            ChatUsersApi.addContact(
                                                token = sessionToken,
                                                userId = result.id,
                                                onSuccess = {
                                                    mainHandler.post {
                                                        addingContactId = null
                                                        searchMessage =
                                                            "${result.loginId} added to Chats."
                                                        searchResults =
                                                            searchResults.filterNot {
                                                                it.id == result.id
                                                            }
                                                        onRefreshContacts()
                                                    }
                                                },
                                                onError = { error ->
                                                    mainHandler.post {
                                                        addingContactId = null
                                                        searchMessage = error
                                                    }
                                                }
                                            )
                                        }
                                    },
                                    enabled = addingContactId == null
                                ) {
                                    if (addingContactId == result.id) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text("Add")
                                    }
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }

            HorizontalDivider()

            when {
                isLoadingUsers -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                chatUsers.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No other users available")
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = chatUsers,
                            key = { user -> user.id }
                        ) { user ->
                            val lastMessage = messages.lastOrNull {
                                (it.sender == currentUser &&
                                        it.receiver == user.loginId) ||
                                        (it.sender == user.loginId &&
                                                it.receiver == currentUser)
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onUserSelected(user.loginId)
                                        messageText = ""
                                        sendError = null
                                        showConversation = true
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 16.dp,
                                            vertical = 14.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(50.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = user.loginId
                                                    .take(1)
                                                    .uppercase(),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = user.loginId,
                                            style = MaterialTheme.typography.titleMedium
                                        )

                                        Spacer(Modifier.height(4.dp))

                                        Text(
                                            text = lastMessage?.let {
                                                if (it.sender == currentUser) {
                                                    "You: ${it.message}"
                                                } else {
                                                    it.message
                                                }
                                            } ?: "Tap to start chatting",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )

                                        Spacer(Modifier.height(3.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(
                                                        color = if (
                                                            onlineUsers.contains(user.loginId)
                                                        ) {
                                                            androidx.compose.ui.graphics.Color(0xFF22C55E)
                                                        } else {
                                                            MaterialTheme.colorScheme.outline
                                                        },
                                                        shape = CircleShape
                                                    )
                                            )

                                            Spacer(Modifier.width(5.dp))

                                            Text(
                                                text = if (
                                                    onlineUsers.contains(user.loginId)
                                                ) "Online" else "Offline",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        showConversation = false
                        messageText = ""
                        sendError = null
                    }
                ) {
                    Text(
                        "‹",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = receiver.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = receiver,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (onlineUsers.contains(receiver)) {
                            "Online"
                        } else {
                            "Offline"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(onClick = onLogout) {
                    Text(
                        "Logout",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            connectionError?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                items(conversation) { chatMessage ->
                    val isMine = chatMessage.sender == currentUser

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (isMine) {
                            Arrangement.End
                        } else {
                            Arrangement.Start
                        }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 14.dp,
                                bottomStart = if (isMine) 14.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 14.dp
                            ),
                            color = if (isMine) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth(0.78f)
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 8.dp
                                )
                            ) {
                                Text(
                                    text = chatMessage.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isMine) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )


                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = {
                        messageText = it
                        sendError = null
                    },
                    placeholder = { Text("Message") },
                    enabled = connectionStatus == "Connected",
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 2.dp),
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = {
                        if (receiver.isBlank()) {
                            sendError = "Select a user first."
                        } else if (messageText.isBlank()) {
                            sendError = "Enter a message."
                        } else {
                            val sent = onSendMessage(
                                receiver,
                                messageText.trim()
                            )

                            if (sent) {
                                messageText = ""
                                sendError = null
                            } else {
                                sendError = "Message was not sent."
                            }
                        }
                    },
                    enabled = connectionStatus == "Connected" &&
                            messageText.isNotBlank(),
                    shape = CircleShape
                ) {
                    Text("Send")
                }
            }

            sendError?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = 8.dp
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
