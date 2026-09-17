package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.data.FamilyRepository
import com.example.ui.components.QadirLogoBadge
import com.example.ui.components.QadirPrimaryButton
import com.example.ui.components.QadirTopAppBar
import com.example.ui.theme.*

@Composable
fun ForgotPasswordScreen(
    repository: FamilyRepository,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var cnic by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun handleResetPassword() {
        if (email.isBlank() || cnic.isBlank()) {
            errorMessage = "Please enter both Gmail and CNIC."
            successMessage = null
            return
        }

        isLoading = true
        errorMessage = null
        successMessage = null

        scope.launch {
            val result = repository.requestPasswordReset(email.trim(), cnic.trim())
            isLoading = false
            result.fold(
                onSuccess = {
                    successMessage = "If the information matches a registered family account, a password reset link has been sent to your registered Gmail."
                },
                onFailure = {
                    errorMessage = it.message ?: "Failed to process request. Please check your connection."
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        CreamBackground,
                        LightLeafGreen.copy(alpha = 0.35f),
                        CreamBackground
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QadirTopAppBar(title = "", onBack = onBack)
            Spacer(modifier = Modifier.height(16.dp))

            QadirLogoBadge(size = 72.dp)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Password Recovery",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreen,
                fontFamily = FontFamily.Serif
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Verify your identity to reset your password",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = BarkBrown,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Input fields
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null; successMessage = null },
                    label = { Text("Gmail / Email") },
                    placeholder = { Text("Enter your registered email") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = ForestGreen)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CreamSurface,
                        unfocusedContainerColor = CreamSurface,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = cnic,
                    onValueChange = { cnic = it; errorMessage = null; successMessage = null },
                    label = { Text("CNIC Number") },
                    placeholder = { Text("e.g. 35201-1234567-1") },
                    leadingIcon = {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = ForestGreen)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CreamSurface,
                        unfocusedContainerColor = CreamSurface,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            if (successMessage != null) {
                Text(
                    text = successMessage ?: "",
                    color = ForestGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(color = ForestGreen)
            } else {
                QadirPrimaryButton(
                    text = "Send Reset Link",
                    onClick = ::handleResetPassword,
                    backgroundColor = ForestGreen
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
