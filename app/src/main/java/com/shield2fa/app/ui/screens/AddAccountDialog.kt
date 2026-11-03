package com.shield2fa.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shield2fa.app.model.AccountItem
import com.shield2fa.app.ui.theme.Indigo400
import com.shield2fa.app.ui.theme.Slate400
import com.shield2fa.app.ui.theme.Slate800

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onAccountAdded: (AccountItem) -> Unit
) {
    var issuer by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate800,
        title = {
            Text(
                text = "Add Account Manually",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = issuer,
                    onValueChange = { issuer = it },
                    label = { Text("Service (e.g. GitHub, Instagram)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("Account Name / Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = secret,
                    onValueChange = {
                        secret = it.uppercase().replace(" ", "")
                        errorMessage = null
                    },
                    label = { Text("Secret Key (Base32)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanSecret = secret.trim().replace(" ", "")
                    if (cleanSecret.isBlank()) {
                        errorMessage = "Secret key is required"
                    } else if (cleanSecret.any { !it.isLetterOrDigit() }) {
                        errorMessage = "Secret contains invalid characters"
                    } else {
                        val finalIssuer = if (issuer.isBlank()) "Service" else issuer.trim()
                        val finalAccount = if (accountName.isBlank()) "Account" else accountName.trim()
                        onAccountAdded(
                            AccountItem(
                                issuer = finalIssuer,
                                accountName = finalAccount,
                                secret = cleanSecret
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo400),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Add Account", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate400)
            }
        }
    )
}
