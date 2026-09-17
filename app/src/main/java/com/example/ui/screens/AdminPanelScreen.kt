package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyRepository
import com.example.model.FamilyMember
import com.example.model.PendingRegistration
import com.example.ui.components.MemberAvatar
import com.example.ui.components.QadirTopAppBar
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    repository: FamilyRepository,
    onNavigateToAddMember: () -> Unit,
    onNavigateToDocuments: (String?) -> Unit,
    onLogout: () -> Unit
) {
    val members by repository.members.collectAsState()
    val settings by repository.settings.collectAsState()
    val pendingRegistrations by repository.pendingRegistrations.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: All Members, 1: Pending Verification
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var memberToEdit by remember { mutableStateOf<FamilyMember?>(null) }
    var memberToMove by remember { mutableStateOf<FamilyMember?>(null) }
    var memberToDelete by remember { mutableStateOf<FamilyMember?>(null) }

    val filteredMembers = members.filter {
        it.fullName.contains(searchQuery, ignoreCase = true) ||
        it.relation.contains(searchQuery, ignoreCase = true) ||
        it.generation.toString() == searchQuery ||
        (it.email != null && it.email.contains(searchQuery, ignoreCase = true))
    }

    val pendingMembers = members.filter { !it.isApproved }
    val filteredPendingMembers = pendingMembers.filter {
        it.fullName.contains(searchQuery, ignoreCase = true) ||
        it.relation.contains(searchQuery, ignoreCase = true) ||
        (it.email != null && it.email.contains(searchQuery, ignoreCase = true))
    }

    val filteredPendingRegs = pendingRegistrations.filter {
        it.fullName.contains(searchQuery, ignoreCase = true) ||
        it.email.contains(searchQuery, ignoreCase = true) ||
        it.phone.contains(searchQuery, ignoreCase = true) ||
        it.fatherName.contains(searchQuery, ignoreCase = true) ||
        it.motherName.contains(searchQuery, ignoreCase = true)
    }
    val totalPendingCount = pendingMembers.size + pendingRegistrations.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = HeritageGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Admin Panel",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = HeritageGold,
                            fontFamily = FontFamily.Serif
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout", tint = HeritageGoldLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AdminSurfaceDark)
            )
        },
        containerColor = AdminBgDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Quick Actions Grid
            Text(
                text = "Quick Actions",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = HeritageGoldLight,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminActionCard(
                    icon = Icons.Default.PersonAdd,
                    label = "Add Member",
                    onClick = onNavigateToAddMember,
                    modifier = Modifier.weight(1f)
                )
                AdminActionCard(
                    icon = Icons.Default.AltRoute,
                    label = "Move Branch",
                    onClick = {
                        if (members.isNotEmpty()) {
                            memberToMove = members.firstOrNull { it.generation >= 1 }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                AdminActionCard(
                    icon = Icons.Default.Description,
                    label = "Documents",
                    onClick = { onNavigateToDocuments(null) },
                    modifier = Modifier.weight(1f)
                )
                AdminActionCard(
                    icon = Icons.Default.Key,
                    label = "Passcode",
                    onClick = { showPasscodeDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs: All Members vs Pending Verification
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = AdminSurfaceDark,
                contentColor = HeritageGold,
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("All Members (${filteredMembers.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pending Verification", fontWeight = FontWeight.Bold)
                            if (totalPendingCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFFF6B6B)
                                ) {
                                    Text(
                                        text = "$totalPendingCount",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (selectedTab == 0) "Search all members..." else "Search pending verification...", color = HeritageGoldLight.copy(alpha = 0.6f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = HeritageGold) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BarkBrown,
                    unfocusedTextColor = BarkBrown,
                    focusedContainerColor = AdminSurfaceDark,
                    unfocusedContainerColor = AdminSurfaceDark,
                    focusedBorderColor = HeritageGold,
                    unfocusedBorderColor = AdminBorderDark
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedTab == 0) "All Members (${filteredMembers.size})" else "Pending Verification Requests (${filteredPendingMembers.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeritageGoldLight
                )
                Text(
                    text = "Passcode: ${settings.familyPasscode}",
                    fontSize = 12.sp,
                    color = HeritageGold,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedTab == 0) {
                // All Members List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMembers, key = { it.id }) { member ->
                        MemberManagementCard(
                            member = member,
                            onEdit = { memberToEdit = member },
                            onMove = { memberToMove = member },
                            onDelete = { memberToDelete = member },
                            onApprove = null
                        )
                    }
                }
            } else {
                // Pending Verification List
                if (filteredPendingMembers.isEmpty() && filteredPendingRegs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ForestGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No pending verification requests",
                                color = BarkBrown,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All registered members have been approved.",
                                color = HeritageGoldLight,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPendingRegs, key = { it.id }) { reg ->
                            RegistrationManagementCard(
                                reg = reg,
                                onApprove = {
                                    coroutineScope.launch {
                                        val res = repository.approveRegistration(reg)
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Approved ${reg.fullName} successfully into family tree!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Approval error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                        }
                        items(filteredPendingMembers, key = { it.id }) { member ->
                            MemberManagementCard(
                                member = member,
                                onEdit = { memberToEdit = member },
                                onMove = { memberToMove = member },
                                onDelete = { memberToDelete = member },
                                onApprove = {
                                    repository.approveMember(member.id)
                                    Toast.makeText(context, "Approved ${member.fullName} successfully!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Change Passcode Dialog
        if (showPasscodeDialog) {
            var newPasscode by remember { mutableStateOf(settings.familyPasscode) }
            AlertDialog(
                onDismissRequest = { showPasscodeDialog = false },
                title = { Text("Change Family Passcode", color = ForestGreen, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "New members must enter this 5-digit passcode to register into the Qadir Family network.",
                            fontSize = 12.sp,
                            color = CreamMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newPasscode,
                            onValueChange = { if (it.length <= 5) newPasscode = it },
                            label = { Text("5-Digit Passcode") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPasscode.length == 5) {
                                repository.updatePasscode(newPasscode)
                                showPasscodeDialog = false
                                Toast.makeText(context, "Passcode updated to $newPasscode!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Passcode must be 5 digits.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                    ) {
                        Text("Save Passcode")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasscodeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Edit Member Dialog
        if (memberToEdit != null) {
            val target = memberToEdit!!
            var editName by remember { mutableStateOf(target.fullName) }
            var editRelation by remember { mutableStateOf(target.relation) }
            var editPhone by remember { mutableStateOf(target.phone ?: "") }
            var editCity by remember { mutableStateOf(target.city ?: "Lahore") }

            AlertDialog(
                onDismissRequest = { memberToEdit = null },
                title = { Text("Edit Member Details", color = ForestGreen, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Full Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editRelation,
                            onValueChange = { editRelation = it },
                            label = { Text("Relation") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editPhone,
                            onValueChange = { editPhone = it },
                            label = { Text("Phone Number") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editCity,
                            onValueChange = { editCity = it },
                            label = { Text("City") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            repository.updateMember(
                                target.copy(
                                    fullName = editName.trim(),
                                    relation = editRelation.trim(),
                                    phone = editPhone.trim(),
                                    city = editCity.trim()
                                )
                            )
                            memberToEdit = null
                            Toast.makeText(context, "Member updated successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                    ) {
                        Text("Save Changes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memberToEdit = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Move Branch Dialog
        if (memberToMove != null) {
            val target = memberToMove!!
            var selectedNewParentId by remember { mutableStateOf(members.firstOrNull { it.id != target.id }?.id ?: "") }
            var parentMenuExpanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { memberToMove = null },
                title = { Text("Move Branch / Change Parent", color = ForestGreen, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            text = "Reparent ${target.fullName} under another branch. The tree will automatically recalculate.",
                            fontSize = 12.sp,
                            color = CreamMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val newParent = members.find { it.id == selectedNewParentId }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = newParent?.fullName ?: "Select New Parent",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { parentMenuExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = parentMenuExpanded,
                                onDismissRequest = { parentMenuExpanded = false }
                            ) {
                                members.filter { it.id != target.id }.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text("${p.fullName} (${p.relation})") },
                                        onClick = {
                                            selectedNewParentId = p.id
                                            parentMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            repository.moveMemberBranch(target.id, selectedNewParentId)
                            memberToMove = null
                            Toast.makeText(context, "Branch moved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                    ) {
                        Text("Move")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memberToMove = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Delete Member Dialog
        if (memberToDelete != null) {
            val target = memberToDelete!!
            AlertDialog(
                onDismissRequest = { memberToDelete = null },
                title = { Text("Delete Family Member", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold) },
                text = {
                    Text("Are you sure you want to remove ${target.fullName} from the family tree? All associated documents will also be removed.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            repository.deleteMember(target.id)
                            memberToDelete = null
                            Toast.makeText(context, "${target.fullName} removed from tree.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { memberToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun AdminActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(76.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AdminSurfaceDark),
        border = BorderStroke(1.dp, AdminBorderDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = HeritageGold,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = BarkBrown,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MemberManagementCard(
    member: FamilyMember,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onApprove: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AdminSurfaceDark),
        border = BorderStroke(1.dp, if (onApprove != null) Color(0xFFFFB300).copy(alpha = 0.6f) else AdminBorderDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatar(
                name = member.fullName,
                size = 44.dp,
                borderColor = if (onApprove != null) Color(0xFFFFB300) else HeritageGold,
                isRoot = member.generation == 0,
                backgroundColor = AdminCardDark
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.fullName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BarkBrown
                    )
                    if (onApprove != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "Pending",
                                color = Color(0xFF8F6B00),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${member.relation}  ·  Gen ${member.generation}${if (!member.email.isNullOrEmpty()) "  ·  ${member.email}" else ""}",
                    fontSize = 11.sp,
                    color = HeritageGoldLight.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onApprove != null) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Approve", fontSize = 11.sp)
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Member",
                        tint = HeritageGold,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AltRoute,
                        contentDescription = "Move Branch",
                        tint = LeafGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (member.generation > 0) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Member",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistrationManagementCard(
    reg: PendingRegistration,
    onApprove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AdminSurfaceDark),
        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatar(
                name = reg.fullName,
                size = 44.dp,
                borderColor = Color(0xFFFFB300),
                isRoot = false,
                backgroundColor = AdminCardDark
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reg.fullName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BarkBrown
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (reg.isRootParent) Color(0xFFD4AF37).copy(alpha = 0.25f) else Color(0xFFFFB300).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (reg.isRootParent) "Root Parent" else "New Reg",
                            color = if (reg.isRootParent) Color(0xFFB8860B) else Color(0xFF8F6B00),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                val parentDetail = if (reg.isRootParent) "Root Trunk Founder (${reg.gender})" else if (reg.fatherName.isNotBlank()) "Father: ${reg.fatherName}" else if (reg.motherName.isNotBlank()) "Mother: ${reg.motherName}" else "Pending Branch"
                Text(
                    text = "$parentDetail  ·  ${reg.email.ifEmpty { reg.phone }}",
                    fontSize = 11.sp,
                    color = HeritageGoldLight.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }

            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "Approve", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Approve", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

