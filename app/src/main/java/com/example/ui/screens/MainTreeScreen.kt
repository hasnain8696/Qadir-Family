package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyRepository
import com.example.model.FamilyMember
import com.example.ui.components.LivingTreeCanvas
import com.example.ui.components.MemberAvatar
import com.example.ui.components.QadirLogoBadge
import com.example.ui.components.QadirPrimaryButton
import com.example.ui.theme.*
import com.example.util.FamilyTreeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTreeScreen(
    repository: FamilyRepository,
    onNavigateToBranchZoom: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    onNavigateToDocuments: (String?) -> Unit,
    onNavigateToAddMember: () -> Unit,
    onNavigateToAddChild: ((String) -> Unit)? = null,
    onNavigateToAdmin: () -> Unit,
    onLogout: () -> Unit
) {
    val members by repository.members.collectAsState()
    val currentUser by repository.currentUser.collectAsState()

    var selectedMemberForAction by remember { mutableStateOf<FamilyMember?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Tree, 1 = Directory
    var searchQuery by remember { mutableStateOf("") }

    // Identify Root Parents and 1st generation children using FamilyTreeUtils
    val (rootFather, rootMother) = remember(members) {
        FamilyTreeUtils.findRootParents(members)
    }

    val rootParentsList = remember(rootFather, rootMother) {
        listOfNotNull(rootFather, rootMother)
    }

    val firstGenChildren = remember(members, rootFather, rootMother) {
        FamilyTreeUtils.findFirstGenChildren(members, rootFather, rootMother)
    }

    val loggedInMember = remember(members, currentUser) {
        repository.getLoggedInMember()
    }
    val loggedInMemberId = loggedInMember?.id

    val onAddMemberClick: () -> Unit = {
        if (currentUser?.role != "admin" && loggedInMemberId != null) {
            onNavigateToAddChild?.invoke(loggedInMemberId) ?: onNavigateToAddMember()
        } else {
            onNavigateToAddMember()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QadirLogoBadge(size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Qadir Family Tree",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                text = "One Family · One Tree · Forever",
                                fontSize = 10.sp,
                                color = HeritageGoldDark,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onAddMemberClick,
                        modifier = Modifier.testTag("add_member_top_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add Family Member",
                            tint = ForestGreen
                        )
                    }
                    if (currentUser?.role == "admin") {
                        IconButton(onClick = onNavigateToAdmin) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin Panel",
                                tint = HeritageGoldDark
                            )
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "Logout",
                            tint = BarkBrown
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CreamSurface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = CreamSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Nature, contentDescription = "Tree") },
                    label = { Text("Living Tree", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ForestGreen,
                        selectedTextColor = ForestGreen,
                        indicatorColor = LightLeafGreen
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Group, contentDescription = "Directory") },
                    label = { Text("Directory", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ForestGreen,
                        selectedTextColor = ForestGreen,
                        indicatorColor = LightLeafGreen
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigateToDocuments(null) },
                    icon = { Icon(Icons.Default.Description, contentDescription = "Documents") },
                    label = { Text("Documents", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ForestGreen,
                        selectedTextColor = ForestGreen,
                        indicatorColor = LightLeafGreen
                    )
                )
                if (currentUser?.role == "admin") {
                    NavigationBarItem(
                        selected = false,
                        onClick = onNavigateToAdmin,
                        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin") },
                        label = { Text("Admin", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ForestGreen,
                            selectedTextColor = ForestGreen,
                            indicatorColor = LightLeafGreen
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = onAddMemberClick,
                    containerColor = ForestGreen,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.testTag("fab_add_member")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Child / Member")
                }
            }
        },
        containerColor = CreamBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> {
                    // Natural Living Family Tree Canvas
                    LivingTreeCanvas(
                        rootParents = rootParentsList,
                        firstGenChildren = firstGenChildren,
                        allMembers = members,
                        loggedInMemberId = loggedInMemberId,
                        onMemberClick = { member ->
                            selectedMemberForAction = member
                        },
                        onNavigateToAddMember = onAddMemberClick
                    )
                }
                1 -> {
                    // Members Directory List
                    MembersDirectoryView(
                        members = members,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onMemberClick = { member ->
                            selectedMemberForAction = member
                        }
                    )
                }
            }

            // Interactive Action Modal Sheet for selected member
            if (selectedMemberForAction != null) {
                val member = selectedMemberForAction!!
                val directChildren = FamilyTreeUtils.findChildrenOf(member, members)
                val canAddChild = repository.canUserAddChildTo(member.id)

                MemberActionBottomSheet(
                    member = member,
                    directChildrenCount = directChildren.size,
                    canAddChild = canAddChild,
                    onDismiss = { selectedMemberForAction = null },
                    onViewProfile = {
                        selectedMemberForAction = null
                        onNavigateToProfile(member.id)
                    },
                    onViewDocuments = {
                        selectedMemberForAction = null
                        onNavigateToDocuments(member.id)
                    },
                    onViewBranch = {
                        selectedMemberForAction = null
                        onNavigateToBranchZoom(member.id)
                    },
                    onAddChild = {
                        selectedMemberForAction = null
                        onNavigateToAddChild?.invoke(member.id) ?: onNavigateToAddMember()
                    }
                )
            }
        }
    }
}

@Composable
private fun MembersDirectoryView(
    members: List<FamilyMember>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onMemberClick: (FamilyMember) -> Unit
) {
    val filtered = members.filter {
        it.fullName.contains(searchQuery, ignoreCase = true) ||
        it.relation.contains(searchQuery, ignoreCase = true) ||
        (it.city?.contains(searchQuery, ignoreCase = true) == true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Search family members...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = ForestGreen) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = CreamSurface,
                unfocusedContainerColor = CreamSurface,
                focusedBorderColor = ForestGreen
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "All Family Members (${filtered.size})",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = ForestGreen
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            filtered.forEach { member ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMemberClick(member) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CreamSurface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(
                            name = member.fullName,
                            imageUrl = member.profileImageUrl,
                            size = 46.dp,
                            borderColor = if (member.generation == 0) HeritageGold else ForestGreen,
                            isRoot = member.generation == 0
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.fullName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = BarkBrown
                            )
                            Text(
                                text = "${member.relation}  ·  Generation ${member.generation}  ·  DOB: ${member.dateOfBirth ?: "N/A"}",
                                fontSize = 11.sp,
                                color = CreamMuted
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = ForestGreen
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberActionBottomSheet(
    member: FamilyMember,
    directChildrenCount: Int,
    canAddChild: Boolean = true,
    onDismiss: () -> Unit,
    onViewProfile: () -> Unit,
    onViewDocuments: () -> Unit,
    onViewBranch: () -> Unit,
    onAddChild: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CreamSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MemberAvatar(
                name = member.fullName,
                imageUrl = member.profileImageUrl,
                size = 76.dp,
                borderColor = HeritageGold,
                isRoot = member.generation == 0
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = member.fullName,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreen,
                fontFamily = FontFamily.Serif
            )

            Text(
                text = "${member.relation} · Generation ${member.generation}${if (!member.dateOfBirth.isNullOrBlank()) " · Born ${member.dateOfBirth}" else ""}",
                fontSize = 12.sp,
                color = HeritageGoldDark,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Branch Zoom Action
                QadirPrimaryButton(
                    text = "Explore Branch Lineage (Zoom)",
                    onClick = onViewBranch,
                    leadingIcon = Icons.Default.Nature,
                    backgroundColor = ForestGreen
                )

                // View Profile Action
                OutlinedButton(
                    onClick = onViewProfile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    border = BorderStroke(1.5.dp, ForestGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Full Profile", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                // View Documents Action
                OutlinedButton(
                    onClick = onViewDocuments,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    border = BorderStroke(1.5.dp, EmeraldGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Family Documents", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                // Add Child to this Branch (Permitted only for own branch or admin)
                if (canAddChild) {
                    OutlinedButton(
                        onClick = onAddChild,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(25.dp),
                        border = BorderStroke(1.2.dp, ForestGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Child to this Branch", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
