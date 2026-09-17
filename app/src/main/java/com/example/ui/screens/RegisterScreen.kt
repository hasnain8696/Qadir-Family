package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.data.FamilyRepository
import com.example.model.FamilyMember
import com.example.ui.components.QadirLogoBadge
import com.example.ui.components.QadirPrimaryButton
import com.example.ui.components.QadirTopAppBar
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    repository: FamilyRepository,
    onRegisterSuccess: (String) -> Unit,
    onBack: () -> Unit
) {
    val members by repository.members.collectAsState()
    val scope = rememberCoroutineScope()

    val rootParentsCount = remember(members) { members.count { it.generation == 0 } }
    val canRegisterRoot = remember(rootParentsCount) { rootParentsCount < 2 }
    val existingRoots = remember(members) { members.filter { it.generation == 0 } }

    var isRootParent by remember { mutableStateOf(false) }

    LaunchedEffect(canRegisterRoot) {
        if (!canRegisterRoot && isRootParent) {
            isRootParent = false
        }
    }
    var gender by remember { mutableStateOf("Male") }
    var fullName by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var cnic by remember { mutableStateOf("") }
    var fatherName by remember { mutableStateOf("") }
    var selectedFatherId by remember { mutableStateOf<String?>(null) }
    var motherName by remember { mutableStateOf("") }
    var selectedMotherId by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+92 ") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var showFatherDropdown by remember { mutableStateOf(false) }
    var showMotherDropdown by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val fathers = members.filter { it.gender == "Male" }
    val mothers = members.filter { it.gender == "Female" }

    val scrollState = rememberScrollState()

    fun handleRegister() {
        if (fullName.isBlank()) {
            errorMessage = "Please enter your full name."
            return
        }
        if (dateOfBirth.isBlank()) {
            errorMessage = "Please enter your Date of Birth (e.g. 15-08-1980) for family tree ordering."
            return
        }
        if (email.isBlank() || !email.contains("@")) {
            errorMessage = "Please enter a valid email address."
            return
        }
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters."
            return
        }
        if (password != confirmPassword) {
            errorMessage = "Passwords do not match."
            return
        }
        if (isRootParent && !canRegisterRoot) {
            errorMessage = "Maximum of 2 Root Parents are already registered on the family tree trunk."
            return
        }

        isLoading = true
        errorMessage = null

        scope.launch {
            val result = repository.registerMember(
                fullName = fullName.trim(),
                cnic = cnic.trim(),
                fatherName = fatherName.trim(),
                motherName = motherName.trim(),
                email = email.trim(),
                phone = phone.trim(),
                password = password,
                selectedFatherId = selectedFatherId,
                selectedMotherId = selectedMotherId,
                isRootParent = isRootParent && canRegisterRoot,
                gender = gender,
                dateOfBirth = dateOfBirth.trim()
            )
            isLoading = false
            result.fold(
                onSuccess = {
                    onRegisterSuccess(email.trim())
                },
                onFailure = { ex ->
                    errorMessage = ex.message ?: "Registration failed."
                }
            )
        }
    }

    Scaffold(
        topBar = {
            QadirTopAppBar(title = "Register", onBack = onBack)
        },
        containerColor = CreamBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QadirLogoBadge(size = 56.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Qadir Family Tree",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreen,
                fontFamily = FontFamily.Serif
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Join the Heritage Lineage",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = BarkBrown
            )
            Text(
                text = "Register to appear on the living family tree and access documents.",
                fontSize = 12.sp,
                color = CreamMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            // Step 1: Placement Category (Root Parent vs Family Member)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CreamSurface),
                border = BorderStroke(1.dp, HeritageGold.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "1. Family Tree Placement",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = HeritageGold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (canRegisterRoot) {
                        // Option A: Root Parent / Trunk
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isRootParent) ForestGreen.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { isRootParent = true }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isRootParent,
                                onClick = { isRootParent = true },
                                colors = RadioButtonDefaults.colors(selectedColor = ForestGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "A. ROOT PARENT / TRUNK",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRootParent) ForestGreen else BarkBrown
                                )
                                val remainingText = when {
                                    rootParentsCount == 0 -> "2 slots remaining globally across family"
                                    rootParentsCount == 1 && existingRoots.firstOrNull()?.gender == "Male" -> "1 slot remaining (Female founder)"
                                    rootParentsCount == 1 && existingRoots.firstOrNull()?.gender == "Female" -> "1 slot remaining (Male founder)"
                                    else -> "1 slot remaining globally"
                                }
                                Text(
                                    text = "$remainingText (${rootParentsCount}/2 registered)",
                                    fontSize = 11.sp,
                                    color = CreamMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                    } else {
                        // Root parents full notice
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ForestGreenDark.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = HeritageGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tree Trunk Founders Registered (2/2)",
                                    fontSize = 12.sp,
                                    color = HeritageGoldLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Option B: Family Member
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isRootParent) ForestGreen.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { isRootParent = false }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isRootParent,
                            onClick = { isRootParent = false },
                            colors = RadioButtonDefaults.colors(selectedColor = ForestGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "B. FAMILY MEMBER (Branch Lineage)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isRootParent) ForestGreen else BarkBrown
                            )
                            Text(
                                text = "Children, grandchildren & descendants of the family",
                                fontSize = 11.sp,
                                color = CreamMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Member Details & Credentials
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Gender Selection (Required for all)
                Column {
                    Text(
                        text = "Gender *",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ForestGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (gender == "Male") ForestGreen.copy(alpha = 0.2f) else CreamSurface,
                            border = BorderStroke(
                                1.5.dp,
                                if (gender == "Male") ForestGreen else CreamMuted.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { gender = "Male" }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                RadioButton(
                                    selected = gender == "Male",
                                    onClick = { gender = "Male" },
                                    colors = RadioButtonDefaults.colors(selectedColor = ForestGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isRootParent) "Male (Father)" else "Male",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = BarkBrown
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (gender == "Female") ForestGreen.copy(alpha = 0.2f) else CreamSurface,
                            border = BorderStroke(
                                1.5.dp,
                                if (gender == "Female") ForestGreen else CreamMuted.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { gender = "Female" }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                RadioButton(
                                    selected = gender == "Female",
                                    onClick = { gender = "Female" },
                                    colors = RadioButtonDefaults.colors(selectedColor = ForestGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isRootParent) "Female (Mother)" else "Female",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = BarkBrown
                                )
                            }
                        }
                    }
                }

                // Full Name
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; errorMessage = null },
                    label = { Text("Full Name *") },
                    placeholder = { Text("e.g. Muhammad Ali Qadir") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = ForestGreen) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CreamSurface,
                        unfocusedContainerColor = CreamSurface,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                // Date of Birth (Required for Tree Age Ordering)
                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it; errorMessage = null },
                    label = { Text("Date of Birth (DD-MM-YYYY) *") },
                    placeholder = { Text("e.g. 15-08-1988") },
                    supportingText = {
                        Text(
                            "Used for automatic tree sibling ordering (oldest centered)",
                            fontSize = 10.sp,
                            color = CreamMuted
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = ForestGreen) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CreamSurface,
                        unfocusedContainerColor = CreamSurface,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                // CNIC Number (Optional)
                OutlinedTextField(
                    value = cnic,
                    onValueChange = { cnic = it; errorMessage = null },
                    label = { Text("CNIC Number (Private)") },
                    placeholder = { Text("e.g. 35201-1234567-1") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = ForestGreen) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CreamSurface,
                        unfocusedContainerColor = CreamSurface,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                // Parent Branch selection (Only if NOT Root Parent)
                if (!isRootParent) {
                    // Father Selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = fatherName,
                            onValueChange = {
                                fatherName = it
                                selectedFatherId = null
                                errorMessage = null
                            },
                            label = { Text("Father's Name / Select Parent") },
                            placeholder = { Text("Choose from family tree or type") },
                            leadingIcon = { Icon(Icons.Default.FamilyRestroom, contentDescription = null, tint = ForestGreen) },
                            trailingIcon = {
                                IconButton(onClick = { showFatherDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Father")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CreamSurface,
                                unfocusedContainerColor = CreamSurface,
                                focusedBorderColor = ForestGreen,
                                unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                            ),
                            singleLine = true
                        )
                        DropdownMenu(
                            expanded = showFatherDropdown,
                            onDismissRequest = { showFatherDropdown = false }
                        ) {
                            fathers.forEach { father ->
                                DropdownMenuItem(
                                    text = { Text("${father.fullName} (${father.relation} - Gen ${father.generation})") },
                                    onClick = {
                                        fatherName = father.fullName
                                        selectedFatherId = father.id
                                        showFatherDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Mother Selection
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = motherName,
                            onValueChange = {
                                motherName = it
                                selectedMotherId = null
                                errorMessage = null
                            },
                            label = { Text("Mother's Name / Select Mother") },
                            placeholder = { Text("Choose from family tree or type") },
                            leadingIcon = { Icon(Icons.Default.Face, contentDescription = null, tint = ForestGreen) },
                            trailingIcon = {
                                IconButton(onClick = { showMotherDropdown = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Mother")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CreamSurface,
                                unfocusedContainerColor = CreamSurface,
                                focusedBorderColor = ForestGreen,
                                unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                            ),
                            singleLine = true
                        )
                        DropdownMenu(
                            expanded = showMotherDropdown,
                            onDismissRequest = { showMotherDropdown = false }
                        ) {
                            mothers.forEach { mother ->
                                DropdownMenuItem(
                                    text = { Text("${mother.fullName} (${mother.relation} - Gen ${mother.generation})") },
                                    onClick = {
                                        motherName = mother.fullName
                                        selectedMotherId = mother.id
                                        showMotherDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Gmail / Email *") },
                    placeholder = { Text("Enter your email for login") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = ForestGreen) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CreamSurface,
                        unfocusedContainerColor = CreamSurface,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                // Phone
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMessage = null },
                    label = { Text("Phone Number") },
                    placeholder = { Text("+92 300 1234567") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = ForestGreen) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CreamSurface,
                        unfocusedContainerColor = CreamSurface,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Password *") },
                    placeholder = { Text("At least 6 characters") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = ForestGreen) },
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
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CreamSurface,
                        unfocusedContainerColor = CreamSurface,
                        focusedBorderColor = ForestGreen,
                        unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                    ),
                    singleLine = true
                )

                // Confirm Password
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMessage = null },
                    label = { Text("Confirm Password *") },
                    placeholder = { Text("Re-enter your password") },
                    leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null, tint = ForestGreen) },
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
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
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Submit Registration
            QadirPrimaryButton(
                text = if (isLoading) "Registering..." else "Complete Registration",
                onClick = ::handleRegister,
                enabled = !isLoading,
                backgroundColor = ForestGreen
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
