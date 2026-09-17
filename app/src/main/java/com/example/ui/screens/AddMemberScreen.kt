package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.FamilyRepository
import com.example.model.FamilyMember
import com.example.ui.components.MemberAvatar
import com.example.ui.components.QadirPrimaryButton
import com.example.ui.components.QadirTopAppBar
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemberScreen(
    initialParentId: String? = null,
    repository: FamilyRepository,
    onMemberAdded: () -> Unit,
    onBack: () -> Unit
) {
    val members by repository.members.collectAsState()
    val context = LocalContext.current

    val currentUser by repository.currentUser.collectAsState()
    val isAdmin = currentUser?.role == "admin"
    val loggedInMember = remember(members, currentUser) {
        repository.getLoggedInMember()
    }

    var fullName by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var relation by remember { mutableStateOf("Son") }
    var dateOfBirth by remember { mutableStateOf("") }
    var cnic by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<String?>(null) }
    var selectedParentId by remember(members, loggedInMember, isAdmin) {
        mutableStateOf(
            if (!isAdmin && loggedInMember != null) {
                loggedInMember.id
            } else {
                initialParentId ?: members.firstOrNull { it.generation <= 1 }?.id ?: members.firstOrNull()?.id ?: ""
            }
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedPhotoUri = uri.toString()
        }
    }

    var relationDropdownExpanded by remember { mutableStateOf(false) }
    var parentDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val relationsList = listOf("Son", "Daughter", "Grandson", "Granddaughter", "Spouse", "Brother", "Sister")
    val selectedParent = members.find { it.id == selectedParentId }

    fun handleSave() {
        if (fullName.isBlank()) {
            errorMessage = "Please enter the member's full name."
            return
        }
        if (dateOfBirth.isBlank()) {
            errorMessage = "Please enter Date of Birth (e.g. 15-08-2005) for proper sibling ordering on the tree."
            return
        }

        val effectiveParent = if (!isAdmin && loggedInMember != null) loggedInMember.id else selectedParentId
        if (effectiveParent.isBlank()) {
            errorMessage = "Please select a parent branch."
            return
        }

        val result = repository.addChild(
            fullName = fullName.trim(),
            relation = relation,
            dateOfBirth = dateOfBirth.trim(),
            cnic = cnic.ifBlank { null },
            parentBranchId = effectiveParent,
            gender = gender,
            profileImageUrl = selectedPhotoUri
        )

        result.fold(
            onSuccess = {
                Toast.makeText(context, "${fullName.trim()} added to the family tree!", Toast.LENGTH_SHORT).show()
                onMemberAdded()
            },
            onFailure = { ex ->
                errorMessage = ex.message ?: "Failed to add member."
            }
        )
    }

    Scaffold(
        topBar = {
            QadirTopAppBar(title = "Add Family Member", onBack = onBack)
        },
        containerColor = CreamBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Photo Picker Circle with wooden/gold border
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(LightLeafGreen)
                    .border(2.5.dp, HeritageGold, CircleShape)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!selectedPhotoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = selectedPhotoUri,
                        contentDescription = "Selected Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Add Photo",
                            tint = HeritageGold,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Add Photo",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HeritageGold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Gender Selection
                Column {
                    Text("Gender *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ForestGreen)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (gender == "Male") ForestGreen.copy(alpha = 0.2f) else CreamSurface,
                            border = BorderStroke(1.5.dp, if (gender == "Male") ForestGreen else CreamMuted.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f).clickable {
                                gender = "Male"
                                if (relation == "Daughter") relation = "Son"
                                if (relation == "Granddaughter") relation = "Grandson"
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                RadioButton(
                                    selected = gender == "Male",
                                    onClick = {
                                        gender = "Male"
                                        if (relation == "Daughter") relation = "Son"
                                        if (relation == "Granddaughter") relation = "Grandson"
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = ForestGreen)
                                )
                                Text("Male", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BarkBrown)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (gender == "Female") ForestGreen.copy(alpha = 0.2f) else CreamSurface,
                            border = BorderStroke(1.5.dp, if (gender == "Female") ForestGreen else CreamMuted.copy(alpha = 0.3f)),
                            modifier = Modifier.weight(1f).clickable {
                                gender = "Female"
                                if (relation == "Son") relation = "Daughter"
                                if (relation == "Grandson") relation = "Granddaughter"
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                RadioButton(
                                    selected = gender == "Female",
                                    onClick = {
                                        gender = "Female"
                                        if (relation == "Son") relation = "Daughter"
                                        if (relation == "Grandson") relation = "Granddaughter"
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = ForestGreen)
                                )
                                Text("Female", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = BarkBrown)
                            }
                        }
                    }
                }

                // Full Name
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; errorMessage = null },
                    label = { Text("Full Name *") },
                    placeholder = { Text("Enter member's full name") },
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

                // Date of Birth (Required for Tree Age Sorting)
                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it; errorMessage = null },
                    label = { Text("Date of Birth (DD-MM-YYYY) *") },
                    placeholder = { Text("e.g. 10-04-1995") },
                    supportingText = {
                        Text("Used to calculate automatic tree branch ordering (oldest child centered)", fontSize = 10.sp, color = CreamMuted)
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

                // Relation Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = relation,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Relation *") },
                        leadingIcon = { Icon(Icons.Default.FamilyRestroom, contentDescription = null, tint = ForestGreen) },
                        trailingIcon = {
                            IconButton(onClick = { relationDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CreamSurface,
                            unfocusedContainerColor = CreamSurface,
                            focusedBorderColor = ForestGreen,
                            unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                        )
                    )

                    DropdownMenu(
                        expanded = relationDropdownExpanded,
                        onDismissRequest = { relationDropdownExpanded = false }
                    ) {
                        relationsList.forEach { rel ->
                            DropdownMenuItem(
                                text = { Text(rel) },
                                onClick = {
                                    relation = rel
                                    relationDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // CNIC Number (Optional)
                OutlinedTextField(
                    value = cnic,
                    onValueChange = { cnic = it },
                    label = { Text("CNIC Number (Private, Optional)") },
                    placeholder = { Text("e.g. 35201-1234567-1") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = ForestGreen) },
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

                // Select Parent Branch (Locked to own branch for normal members, selectable for admin)
                if (!isAdmin && loggedInMember != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = LightLeafGreen.copy(alpha = 0.35f),
                        border = BorderStroke(1.2.dp, ForestGreen.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MemberAvatar(
                                name = loggedInMember.fullName,
                                imageUrl = loggedInMember.profileImageUrl,
                                size = 44.dp,
                                borderColor = HeritageGold,
                                isRoot = loggedInMember.generation == 0
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Adding to Your Branch",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestGreen
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked to your branch",
                                        tint = HeritageGoldDark,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = "${loggedInMember.fullName} (${loggedInMember.relation})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BarkBrown
                                )
                                Text(
                                    text = "Child will be registered directly under your family branch",
                                    fontSize = 10.5.sp,
                                    color = CreamMuted
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (selectedParent != null) "${selectedParent.fullName} (${selectedParent.relation} - Gen ${selectedParent.generation})" else "Choose a branch",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Parent Branch *") },
                            leadingIcon = { Icon(Icons.Default.Nature, contentDescription = null, tint = ForestGreen) },
                            trailingIcon = {
                                IconButton(onClick = { parentDropdownExpanded = true }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CreamSurface,
                                unfocusedContainerColor = CreamSurface,
                                focusedBorderColor = ForestGreen,
                                unfocusedBorderColor = CreamMuted.copy(alpha = 0.4f)
                            )
                        )

                        DropdownMenu(
                            expanded = parentDropdownExpanded,
                            onDismissRequest = { parentDropdownExpanded = false }
                        ) {
                            members.forEach { parent ->
                                DropdownMenuItem(
                                    text = { Text("${parent.fullName} (${parent.relation} - Gen ${parent.generation})") },
                                    onClick = {
                                        selectedParentId = parent.id
                                        parentDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }

            QadirPrimaryButton(
                text = "Save to Living Tree",
                onClick = ::handleSave,
                backgroundColor = ForestGreen
            )
        }
    }
}
