package com.shield2fa.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shield2fa.app.ui.theme.Emerald400
import com.shield2fa.app.ui.theme.Indigo400
import com.shield2fa.app.ui.theme.Slate400
import com.shield2fa.app.ui.theme.Slate800

@Composable
fun ResetPasswordDialog(
    onDismiss: () -> Unit,
    onConfirmReset: (newPassword: String) -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate800,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Emerald400)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Biometric Verified",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Enter a new Master Password to restore access without losing your 2FA tokens.",
                    fontSize = 13.sp,
                    color = Slate400,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        errorMessage = null
                    },
                    label = { Text("New Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Indigo400) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    label = { Text("Confirm New Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Indigo400) },
                    visualTransformation = PasswordVisualTransformation(),
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
                    if (newPassword.length < 6) {
                        errorMessage = "Password must be at least 6 characters"
                    } else if (newPassword != confirmPassword) {
                        errorMessage = "Passwords do not match"
                    } else {
                        onConfirmReset(newPassword)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo400),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save New Password", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Slate400)
            }
        }
    )
}
