package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyRepository
import com.example.ui.components.NumericKeypad
import com.example.ui.components.QadirLogoBadge
import com.example.ui.components.QadirTopAppBar
import com.example.ui.theme.*

@Composable
fun PasscodeScreen(
    repository: FamilyRepository,
    onPasscodeSuccess: () -> Unit,
    onBack: () -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val maxDigits = 5

    fun handleDigit(d: String) {
        if (passcode.length < maxDigits) {
            val updated = passcode + d
            passcode = updated
            errorMessage = null

            if (updated.length == maxDigits) {
                repository.setRegistrationPasscode(updated)
                onPasscodeSuccess()
            }
        }
    }

    fun handleBackspace() {
        if (passcode.isNotEmpty()) {
            passcode = passcode.dropLast(1)
            errorMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        CreamBackground,
                        LightLeafGreen.copy(alpha = 0.4f),
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top App Bar
            QadirTopAppBar(title = "", onBack = onBack)

            // Header info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                QadirLogoBadge(size = 68.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Qadir Family",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreen,
                    fontFamily = FontFamily.Serif
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Enter Family Passcode",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BarkBrown
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "This passcode is required to access the application registration.",
                    fontSize = 13.sp,
                    color = CreamMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Passcode Dots Display
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = CreamSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = ForestGreen,
                            modifier = Modifier.size(20.dp)
                        )

                        for (i in 0 until maxDigits) {
                            val isFilled = i < passcode.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (isFilled) ForestGreen else CreamCard)
                                    .border(
                                        1.5.dp,
                                        if (isFilled) HeritageGold else CreamMuted.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                }

                // Error Message
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Contact the Family Administrator to obtain your family passcode.",
                    fontSize = 12.sp,
                    color = BarkBrown.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Numeric Keypad
            NumericKeypad(
                onDigitClick = ::handleDigit,
                onBackspaceClick = ::handleBackspace,
                modifier = Modifier.padding(bottom = 24.dp),
                keyColor = CreamSurface,
                textColor = ForestGreenDark
            )
        }
    }
}
