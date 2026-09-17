package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.FamilyRepository
import com.example.model.FamilyMember
import com.example.ui.components.MemberAvatar
import com.example.ui.components.QadirPrimaryButton
import com.example.ui.components.QadirTopAppBar
import com.example.ui.components.TreePlaqueMedallion
import com.example.ui.theme.*
import com.example.util.FamilyTreeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchZoomScreen(
    memberId: String,
    repository: FamilyRepository,
    onNavigateToBranchZoom: (String) -> Unit = {},
    onNavigateToProfile: (String) -> Unit,
    onNavigateToAddChild: (String) -> Unit,
    onBackToFullTree: () -> Unit
) {
    val members by repository.members.collectAsState()
    val currentUser by repository.currentUser.collectAsState()
    val loggedInMember = remember(members, currentUser) {
        repository.getLoggedInMember()
    }
    val loggedInMemberId = loggedInMember?.id

    val branchHead = members.find { it.id == memberId } ?: members.firstOrNull()

    // Find direct children of this branch head using FamilyTreeUtils (sorted oldest first)
    val children = remember(branchHead, members) {
        FamilyTreeUtils.findChildrenOf(branchHead, members)
    }

    // Dynamic sibling arrangement with oldest centered
    val arrangedChildren = remember(children) {
        FamilyTreeUtils.arrangeSiblingsWithOldestInCenter(children)
    }

    val oldestChildId = remember(children) {
        children.firstOrNull()?.id
    }

    // Breadcrumb path from Root down to this member
    val ancestorPath = remember(branchHead, members) {
        val path = mutableListOf<FamilyMember>()
        var curr = branchHead
        val visited = mutableSetOf<String>()
        while (curr != null && !visited.contains(curr.id)) {
            visited.add(curr.id)
            path.add(0, curr)
            val (father, mother) = FamilyTreeUtils.findParentsOf(curr, members)
            curr = father ?: mother
        }
        path
    }

    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var selectedMemberForAction by remember { mutableStateOf<FamilyMember?>(null) }

    Scaffold(
        topBar = {
            val canAddChildToBranchHead = currentUser?.role == "admin" || (branchHead != null && repository.canUserAddChildTo(branchHead.id))
            QadirTopAppBar(
                title = "${branchHead?.fullName?.split(" ")?.firstOrNull() ?: "Member"}'s Branch",
                onBack = onBackToFullTree,
                actions = {
                    if (canAddChildToBranchHead) {
                        IconButton(
                            onClick = { branchHead?.id?.let(onNavigateToAddChild) },
                            modifier = Modifier.testTag("add_child_branch_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Add Child",
                                tint = ForestGreen
                            )
                        }
                    }
                }
            )
        },
        containerColor = CreamBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .clipToBounds()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFFDE7),
                            Color(0xFFE8F5E9),
                            Color(0xFFD7CCC8)
                        ),
                        center = Offset(500f, 400f),
                        radius = 1200f
                    )
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.6f, 2.5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1.0f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 1.6f
                            }
                        }
                    )
                }
        ) {
            // Background tree canopy illustration
            Image(
                painter = painterResource(id = R.drawable.img_tree_canopy),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.24f),
                contentScale = ContentScale.Crop
            )

            // Dynamic Natural Branch Canvas
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            ) {
                val canvasW = size.width
                val canvasH = size.height

                val parentX = canvasW * 0.5f
                val parentY = canvasH * 0.72f

                // Draw Parent Branch Trunk Stump
                drawBranchStump(
                    baseX = parentX,
                    baseY = canvasH * 0.95f,
                    topY = parentY
                )

                // Draw upward organic curved branches to each child node
                if (arrangedChildren.isNotEmpty()) {
                    val count = arrangedChildren.size
                    arrangedChildren.forEachIndexed { index, child ->
                        val progress = if (count == 1) 0.5f else index.toFloat() / (count - 1)
                        val childX = canvasW * (0.12f + progress * 0.76f)
                        val arcLift = (0.5f - kotlin.math.abs(progress - 0.5f)) * 0.18f
                        val childY = canvasH * (0.30f - arcLift)

                        drawBranchCurve(
                            startX = parentX,
                            startY = parentY - 40f,
                            endX = childX,
                            endY = childY,
                            isCenter = child.id == oldestChildId
                        )

                        // Foliage cluster
                        drawBranchLeafCluster(
                            centerX = childX,
                            centerY = childY,
                            isOldest = child.id == oldestChildId
                        )
                    }
                }
            }

            // Transformed Placards & Medallions Box
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val boxWidth = maxWidth
                    val boxHeight = maxHeight

                    // 1. Branch Head (Parent) Placard at the Branch Trunk
                    if (branchHead != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = boxHeight * 0.65f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = ForestGreenDark.copy(alpha = 0.92f),
                                    border = BorderStroke(1.2.dp, HeritageGold),
                                    shadowElevation = 4.dp
                                ) {
                                    Text(
                                        text = "BRANCH HEAD · ${branchHead.fullName.uppercase()}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HeritageGoldLight,
                                        letterSpacing = 0.8.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                val spouse = FamilyTreeUtils.findSpouseOf(branchHead, members)
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TreePlaqueMedallion(
                                        member = branchHead,
                                        isOldest = false,
                                        isRoot = branchHead.generation == 0,
                                        allMembers = members,
                                        isLoggedInUser = branchHead.id == loggedInMemberId,
                                        onClick = { selectedMemberForAction = branchHead }
                                    )

                                    if (spouse != null) {
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(HeritageGold)
                                                .border(1.2.dp, Color.White, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "Spouse Union",
                                                tint = ForestGreenDark,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        TreePlaqueMedallion(
                                            member = spouse,
                                            isOldest = false,
                                            isRoot = spouse.generation == 0,
                                            allMembers = members,
                                            isLoggedInUser = spouse.id == loggedInMemberId,
                                            onClick = { selectedMemberForAction = spouse }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Children of this branch
                    if (arrangedChildren.isNotEmpty()) {
                        val count = arrangedChildren.size
                        arrangedChildren.forEachIndexed { index, child ->
                            val progress = if (count == 1) 0.5f else index.toFloat() / (count - 1)
                            val childXFraction = 0.12f + progress * 0.76f
                            val arcLift = (0.5f - kotlin.math.abs(progress - 0.5f)) * 0.18f
                            val childYFraction = 0.30f - arcLift

                            val isOldest = child.id == oldestChildId

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(
                                        x = boxWidth * (childXFraction - 0.5f),
                                        y = boxHeight * childYFraction - 60.dp
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                TreePlaqueMedallion(
                                    member = child,
                                    isOldest = isOldest,
                                    isRoot = false,
                                    allMembers = members,
                                    isLoggedInUser = child.id == loggedInMemberId,
                                    onClick = { selectedMemberForAction = child }
                                )
                            }
                        }
                    } else {
                        // Empty branch notice
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = boxHeight * 0.25f),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = CreamSurface.copy(alpha = 0.95f),
                                border = BorderStroke(1.2.dp, HeritageGold.copy(alpha = 0.6f)),
                                shadowElevation = 4.dp,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No direct children recorded yet.",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestGreen
                                    )
                                    Text(
                                        text = "Add children to grow ${branchHead?.fullName?.split(" ")?.firstOrNull()}'s lineage branch!",
                                        fontSize = 12.sp,
                                        color = CreamMuted,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { branchHead?.id?.let(onNavigateToAddChild) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Add Child to this Branch")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Top Ancestor Breadcrumb Trail
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CreamSurface.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, HeritageGold.copy(alpha = 0.5f)),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🌳 Living Tree",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen,
                        modifier = Modifier.clickable { onBackToFullTree() }
                    )
                    ancestorPath.forEach { anc ->
                        Text(
                            text = " > ",
                            fontSize = 12.sp,
                            color = HeritageGoldDark,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = anc.fullName.split(" ").firstOrNull() ?: anc.fullName,
                            fontSize = 12.sp,
                            fontWeight = if (anc.id == branchHead?.id) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (anc.id == branchHead?.id) HeritageGoldDark else ForestGreen,
                            modifier = Modifier.clickable {
                                if (anc.id != branchHead?.id) {
                                    onNavigateToBranchZoom(anc.id)
                                }
                            }
                        )
                    }
                }
            }

            // Floating Controls (Zoom In, Zoom Out, Reset)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { scale = (scale + 0.25f).coerceAtMost(2.5f) },
                    containerColor = CreamSurface,
                    contentColor = ForestGreen,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(20.dp))
                }

                FloatingActionButton(
                    onClick = { scale = (scale - 0.25f).coerceAtLeast(0.6f) },
                    containerColor = CreamSurface,
                    contentColor = ForestGreen,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(20.dp))
                }

                FloatingActionButton(
                    onClick = {
                        scale = 1.0f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    containerColor = ForestGreen,
                    contentColor = HeritageGold,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset", modifier = Modifier.size(20.dp))
                }
            }

            // Action Bottom Sheet when tapping any member in branch view
            if (selectedMemberForAction != null) {
                val member = selectedMemberForAction!!
                val directChildrenCount = FamilyTreeUtils.findChildrenOf(member, members).size
                val canAddChild = repository.canUserAddChildTo(member.id)

                BranchMemberActionSheet(
                    member = member,
                    isCurrentBranchHead = member.id == branchHead?.id,
                    directChildrenCount = directChildrenCount,
                    canAddChild = canAddChild,
                    onDismiss = { selectedMemberForAction = null },
                    onExploreSubBranch = {
                        selectedMemberForAction = null
                        onNavigateToBranchZoom(member.id)
                    },
                    onViewProfile = {
                        selectedMemberForAction = null
                        onNavigateToProfile(member.id)
                    },
                    onAddChild = {
                        selectedMemberForAction = null
                        onNavigateToAddChild(member.id)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchMemberActionSheet(
    member: FamilyMember,
    isCurrentBranchHead: Boolean,
    directChildrenCount: Int,
    canAddChild: Boolean = true,
    onDismiss: () -> Unit,
    onExploreSubBranch: () -> Unit,
    onViewProfile: () -> Unit,
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
                size = 72.dp,
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
                if (!isCurrentBranchHead) {
                    QadirPrimaryButton(
                        text = "Zoom into ${member.fullName.split(" ").firstOrNull()}'s Branch ($directChildrenCount children)",
                        onClick = onExploreSubBranch,
                        leadingIcon = Icons.Default.Nature,
                        backgroundColor = ForestGreen
                    )
                }

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
                        Text("Add Child to this Member", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

// ==========================================
// Canvas Drawing Helpers for Branch Zoom
// ==========================================

private fun DrawScope.drawBranchStump(
    baseX: Float,
    baseY: Float,
    topY: Float
) {
    val trunkBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF3E2723),
            Color(0xFF5D4037),
            Color(0xFF795548),
            Color(0xFF4E342E)
        ),
        start = Offset(baseX - 45f, topY),
        end = Offset(baseX + 45f, topY)
    )

    val stump = Path().apply {
        moveTo(baseX - 40f, baseY)
        cubicTo(
            baseX - 35f, topY + 40f,
            baseX - 30f, topY + 10f,
            baseX - 25f, topY
        )
        lineTo(baseX + 25f, topY)
        cubicTo(
            baseX + 30f, topY + 10f,
            baseX + 35f, topY + 40f,
            baseX + 40f, baseY
        )
        close()
    }
    drawPath(path = stump, brush = trunkBrush)
}

private fun DrawScope.drawBranchCurve(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    isCenter: Boolean
) {
    val branchBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF5D4037),
            Color(0xFF6D4C41),
            Color(0xFF8D6E63)
        ),
        start = Offset(startX, startY),
        end = Offset(endX, endY)
    )

    val deltaX = endX - startX
    val controlY1 = startY - 70f
    val controlY2 = endY + 70f

    val path = Path().apply {
        moveTo(startX, startY)
        cubicTo(
            startX + deltaX * 0.25f, controlY1,
            endX - deltaX * 0.15f, controlY2,
            endX, endY
        )
    }

    val strokeWidth = if (isCenter) 15f else 11f
    drawPath(
        path = path,
        brush = branchBrush,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawBranchLeafCluster(
    centerX: Float,
    centerY: Float,
    isOldest: Boolean
) {
    val radius = if (isOldest) 62f else 48f

    val leafGreenDark = Color(0xFF1B5E20).copy(alpha = 0.65f)
    val leafGreenMid = Color(0xFF2E7D32).copy(alpha = 0.75f)
    val leafGreenLight = Color(0xFF4CAF50).copy(alpha = 0.85f)
    val goldBlossom = HeritageGold.copy(alpha = 0.65f)

    drawCircle(color = leafGreenDark, radius = radius * 1.1f, center = Offset(centerX, centerY + 6f))
    drawCircle(color = leafGreenMid, radius = radius * 0.9f, center = Offset(centerX - 16f, centerY - 8f))
    drawCircle(color = leafGreenMid, radius = radius * 0.9f, center = Offset(centerX + 16f, centerY - 8f))
    drawCircle(color = leafGreenLight, radius = radius * 0.75f, center = Offset(centerX, centerY - 20f))

    if (isOldest) {
        drawCircle(color = goldBlossom, radius = 7f, center = Offset(centerX - 20f, centerY - 28f))
        drawCircle(color = goldBlossom, radius = 8f, center = Offset(centerX + 20f, centerY - 28f))
        drawCircle(color = goldBlossom, radius = 9f, center = Offset(centerX, centerY - 38f))
    }
}
