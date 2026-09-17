package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyRepository
import com.example.ui.components.QadirLogoBadge
import com.example.ui.components.QadirPrimaryButton
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VerifyEmailScreen(
    email: String,
    repository: FamilyRepository,
    onVerificationSuccess: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isChecking by remember { mutableStateOf(false) }
    var isResending by remember { mutableStateOf(false) }
    var cooldownSeconds by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorMessage by remember { mutableStateOf(false) }

    // Display target email from parameter or repository currentUser
    val displayEmail = remember(email, repository.currentUser.collectAsState().value) {
        if (email.isNotBlank()) email else repository.currentUser.value?.email ?: "your Gmail address"
    }

    // Auto-advance ONLY when BOTH email is verified and admin has approved
    val membersList by repository.members.collectAsState()
    val currentUserState by repository.currentUser.collectAsState()
    val canAccessNow = remember(membersList, currentUserState) {
        repository.canAccessFamilyTree()
    }

    LaunchedEffect(canAccessNow) {
        if (canAccessNow) {
            onVerificationSuccess()
        }
    }

    // Cooldown countdown timer
    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000)
            cooldownSeconds -= 1
        }
    }

    fun openEmailApp() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_EMAIL)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to Gmail package or webmail
            val gmailIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
            if (gmailIntent != null) {
                context.startActivity(gmailIntent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com/"))
                try {
                    context.startActivity(browserIntent)
                } catch (ex: Exception) {
                    Toast.makeText(context, "Could not open email app automatically.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleCheckVerification() {
        isChecking = true
        statusMessage = null
        scope.launch {
            val result = repository.checkEmailVerification()
            isChecking = false
            val isEmailVerified = result.getOrDefault(repository.isCurrentUserEmailVerified())

            if (isEmailVerified) {
                isErrorMessage = false
                statusMessage = "Gmail verified successfully! Welcome to the Qadir Family Tree."
                Toast.makeText(context, "Gmail Verified! Welcome to the Living Tree.", Toast.LENGTH_SHORT).show()
                delay(300)
                onVerificationSuccess()
            } else {
                isErrorMessage = true
                statusMessage = "Your Gmail is not verified yet. Please click the verification link in your inbox and tap Check Verification again."
            }
        }
    }

    fun handleResendVerification() {
        if (cooldownSeconds > 0 || isResending) return
        isResending = true
        statusMessage = null
        scope.launch {
            val result = repository.resendVerificationEmail()
            isResending = false
            result.fold(
                onSuccess = {
                    cooldownSeconds = 45 // 45 seconds cooldown
                    isErrorMessage = false
                    statusMessage = "A fresh verification link has been sent to $displayEmail. Please check your inbox!"
                    Toast.makeText(context, "Verification email resent!", Toast.LENGTH_SHORT).show()
                },
                onFailure = { ex ->
                    isErrorMessage = true
                    statusMessage = ex.message ?: "Failed to resend verification email. Please try again later."
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
                        LightLeafGreen.copy(alpha = 0.3f),
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
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Logo Badge
            QadirLogoBadge(size = 64.dp)
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Qadir Family Tree",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreen,
                fontFamily = FontFamily.Serif
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Verification Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .testTag("verify_email_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = CreamSurface),
                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mail Animation / Icon Badge
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(ForestGreenDark.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MarkEmailRead,
                            contentDescription = "Verify Email",
                            tint = HeritageGold,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Verify Your Gmail",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = BarkBrown,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "We sent an official Firebase verification link to:",
                        fontSize = 13.sp,
                        color = CreamMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = displayEmail,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ForestGreenDark.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Please click the link inside the email to verify your address. Once verified via the link, tap \"I've Verified\" below to enter the Living Tree.",
                        fontSize = 13.sp,
                        color = BarkBrown.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    // Status Message banner
                    AnimatedVisibility(
                        visible = statusMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isErrorMessage) MaterialTheme.colorScheme.errorContainer else ForestGreenDark.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp,
                                if (isErrorMessage) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else ForestGreen.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isErrorMessage) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = if (isErrorMessage) MaterialTheme.colorScheme.error else ForestGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = statusMessage ?: "",
                                    fontSize = 12.sp,
                                    color = if (isErrorMessage) MaterialTheme.colorScheme.onErrorContainer else BarkBrown,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // 1. Open Gmail Button
                    Button(
                        onClick = ::openEmailApp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_gmail_button"),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HeritageGoldDark,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open Gmail / Email App",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Check Verification Button
                    QadirPrimaryButton(
                        text = if (isChecking) "Checking Status..." else "I've Verified / Enter Living Tree",
                        onClick = ::handleCheckVerification,
                        enabled = !isChecking,
                        leadingIcon = if (isChecking) null else Icons.Default.VerifiedUser,
                        backgroundColor = ForestGreen
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Resend Verification Email Button
                    OutlinedButton(
                        onClick = ::handleResendVerification,
                        enabled = cooldownSeconds == 0 && !isResending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("resend_email_button"),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(
                            1.dp,
                            if (cooldownSeconds == 0) ForestGreen.copy(alpha = 0.6f) else CreamMuted.copy(alpha = 0.3f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (cooldownSeconds == 0) ForestGreen else CreamMuted
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isResending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = ForestGreen
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending link...", fontSize = 14.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (cooldownSeconds > 0) "Resend in ${cooldownSeconds}s" else "Resend Verification Email",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sign out / Back to Login
            TextButton(
                onClick = onLogout,
                modifier = Modifier.testTag("logout_or_switch_account_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = null,
                        tint = CreamMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Sign in with another account / Back to Login",
                        color = CreamMuted,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
