package com.shield2fa.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shield2fa.app.ui.theme.Emerald400
import com.shield2fa.app.ui.theme.Indigo400
import com.shield2fa.app.ui.theme.Slate400
import com.shield2fa.app.ui.theme.Slate800
import com.shield2fa.app.ui.theme.Slate900

@Composable
fun UnlockScreen(
    isBiometricAvailable: Boolean,
    onUnlockPassword: (password: String) -> Unit,
    onTriggerBiometrics: (isForReset: Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            tint = Indigo400,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Shield 2FA",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = "Enter master password to unlock your vault",
            fontSize = 14.sp,
            color = Slate400,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                errorMessage = null
            },
            label = { Text("Master Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Indigo400) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Indigo400,
                unfocusedBorderColor = Slate400,
                focusedLabelColor = Indigo400,
                unfocusedLabelColor = Slate400
            )
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (password.isBlank()) {
                    errorMessage = "Please enter your password"
                } else {
                    try {
                        onUnlockPassword(password)
                    } catch (e: Exception) {
                        errorMessage = "Incorrect password"
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Indigo400)
        ) {
            Text(text = "Unlock", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (isBiometricAvailable) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { onTriggerBiometrics(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald400)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Unlock with Fingerprint", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // User requirement: "even if I forget my password, there should be a way for me to refix it maybe using biometrics"
            Text(
                text = "Forgot password? Reset via Fingerprint",
                fontSize = 13.sp,
                color = Indigo400,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clickable { onTriggerBiometrics(true) }
                    .padding(8.dp)
            )
        }
    }
}
