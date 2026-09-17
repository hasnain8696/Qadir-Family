package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
fun LoginScreen(
    repository: FamilyRepository,
    onLoginSuccess: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onBack: () -> Unit
) {
    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    val scope = rememberCoroutineScope()

    fun handleLogin() {
        if (emailOrPhone.isBlank()) {
            errorMessage = "Please enter your Gmail or phone number."
            return
        }
        scope.launch {
            val result = repository.login(emailOrPhone.trim(), password)
            result.fold(
                onSuccess = { onLoginSuccess() },
                onFailure = { errorMessage = it.message ?: "Login failed." }
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QadirTopAppBar(title = "", onBack = onBack)

                Spacer(modifier = Modifier.height(16.dp))
                QadirLogoBadge(size = 72.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Qadir Family",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreen,
                    fontFamily = FontFamily.Serif
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Login to Your Account",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BarkBrown
                )
                Spacer(modifier = Modifier.height(24.dp))

                // Input fields
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = emailOrPhone,
                        onValueChange = { emailOrPhone = it; errorMessage = null },
                        label = { Text("Gmail / Phone Number") },
                        placeholder = { Text("Enter your email or phone") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen)
                        },
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
                        value = password,
                        onValueChange = { password = it; errorMessage = null },
                        label = { Text("Password") },
                        placeholder = { Text("Enter your password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = ForestGreen)
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Forgot Password?",
                            color = ForestGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                onNavigateToForgotPassword()
                            }
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                if (infoMessage != null) {
                    Text(
                        text = infoMessage ?: "",
                        color = ForestGreen,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Login button
                QadirPrimaryButton(
                    text = "Login",
                    onClick = ::handleLogin,
                    backgroundColor = ForestGreen
                )

            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
