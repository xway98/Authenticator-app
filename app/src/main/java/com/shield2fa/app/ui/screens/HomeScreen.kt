package com.shield2fa.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shield2fa.app.crypto.TotpEngine
import com.shield2fa.app.data.VaultRepository
import com.shield2fa.app.model.AccountItem
import com.shield2fa.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vaultRepository: VaultRepository,
    onLockVault: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenManualAdd: () -> Unit,
    onRequestChangePassword: () -> Unit
) {
    val context = LocalContext.current
    var accounts by remember { mutableStateOf(vaultRepository.getAccounts()) }
    var searchQuery by remember { mutableStateOf("") }
    var currentTimeSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000L) }
    var showMenu by remember { mutableStateOf(false) }

    // Live 1-second ticker for TOTP codes and countdown ring
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeSeconds = System.currentTimeMillis() / 1000L
            delay(1000L)
        }
    }

    val remainingSeconds = TotpEngine.getRemainingSeconds()
    val progress = TotpEngine.getProgress()
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    val filteredAccounts = remember(accounts, searchQuery) {
        if (searchQuery.isBlank()) accounts
        else accounts.filter {
            it.issuer.contains(searchQuery, ignoreCase = true) ||
                    it.accountName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = Slate900,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Indigo400)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Shield 2FA",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLockVault) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock", tint = Slate400)
                    }
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Slate400)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Slate800)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Change Master Password", color = Color.White) },
                            leadingIcon = { Icon(Icons.Default.Password, contentDescription = null, tint = Indigo400) },
                            onClick = {
                                showMenu = false
                                onRequestChangePassword()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate900)
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = onOpenManualAdd,
                    containerColor = Slate800,
                    contentColor = Indigo400,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Manual Entry")
                }
                FloatingActionButton(
                    onClick = onOpenQrScanner,
                    containerColor = Indigo400,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search accounts (Instagram, GitHub...)", color = Slate400, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Slate400) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Slate400)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Slate800,
                    unfocusedContainerColor = Slate800,
                    focusedBorderColor = Indigo400,
                    unfocusedBorderColor = Slate700
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            // Progress Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredAccounts.size} account(s)",
                    fontSize = 13.sp,
                    color = Slate400,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(16.dp),
                        color = if (remainingSeconds <= 5) Rose500 else Emerald400,
                        strokeWidth = 2.dp,
                        trackColor = Slate800
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${remainingSeconds}s",
                        fontSize = 13.sp,
                        color = if (remainingSeconds <= 5) Rose500 else Emerald400,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (filteredAccounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = Slate700,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No 2FA accounts yet",
                            fontWeight = FontWeight.SemiBold,
                            color = Slate400,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Tap the camera button below to scan the 2FA QR code from GitHub or Instagram.",
                            color = Slate400.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredAccounts, key = { it.id }) { item ->
                        AccountCard(
                            item = item,
                            currentTimeSeconds = currentTimeSeconds,
                            onCopy = { code ->
                                copyToClipboard(context, code)
                            },
                            onDelete = {
                                accounts = vaultRepository.deleteAccount(item.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccountCard(
    item: AccountItem,
    currentTimeSeconds: Long,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit
) {
    val code = remember(item.secret, currentTimeSeconds) {
        try {
            TotpEngine.generateTotp(
                secretBase32 = item.secret,
                period = item.period,
                digits = item.digits,
                algorithm = item.algorithm,
                timeSeconds = currentTimeSeconds
            )
        } catch (e: Exception) {
            "------"
        }
    }

    val formattedCode = if (code.length == 6) {
        "${code.substring(0, 3)} ${code.substring(3)}"
    } else {
        code
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onCopy(code) },
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.issuer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
                Text(
                    text = item.accountName,
                    fontSize = 13.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                )
                Text(
                    text = formattedCode,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Indigo400
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onCopy(code) }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Code",
                        tint = Slate400
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Slate400
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Slate800,
            title = { Text("Delete 2FA Token?", color = Color.White) },
            text = { Text("Are you sure you want to delete ${item.issuer} (${item.accountName})? You will not be able to recover it without your backup codes.", color = Slate400) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = Slate400)
                }
            }
        )
    }
}

private fun copyToClipboard(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("2FA Code", code))

    // Haptic feedback
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(40)
        }
    } catch (e: Exception) {
        // ignore
    }

    Toast.makeText(context, "Code copied: $code", Toast.LENGTH_SHORT).show()
}
