package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.FamilyMember
import com.example.ui.theme.*
import com.example.util.FamilyTreeUtils
import kotlin.math.cos
import kotlin.math.sin

/**
 * Natural, organic interactive Living Family Tree view.
 * Features:
 * - Dynamic rendering from Firebase/Repository
 * - Organic trunk, roots, curved branches and lush canopy leaves
 * - Root Parents at the trunk foundation
 * - 1st-generation children arranged by Date of Birth with oldest in center (larger medallion)
 * - Pinch-to-zoom, pan, double-tap zoom
 * - Plaque medallions with sub-branch indicators
 * - Personalized glowing highlight for the authenticated logged-in family member
 */
@Composable
fun LivingTreeCanvas(
    rootParents: List<FamilyMember>,
    firstGenChildren: List<FamilyMember>,
    allMembers: List<FamilyMember>,
    loggedInMemberId: String? = null,
    onMemberClick: (FamilyMember) -> Unit,
    onNavigateToAddMember: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Dynamic sibling arrangement with oldest centered
    val arrangedChildren = remember(firstGenChildren) {
        FamilyTreeUtils.arrangeSiblingsWithOldestInCenter(firstGenChildren)
    }

    val oldestChildId = remember(firstGenChildren) {
        FamilyTreeUtils.sortSiblingsByAge(firstGenChildren).firstOrNull()?.id
    }

    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFDE7), // Sunlight warm glow
                        Color(0xFFE8F5E9), // Soft green atmosphere
                        Color(0xFFD7CCC8)  // Earth base
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
        // Natural background tree artwork with low opacity
        Image(
            painter = painterResource(id = R.drawable.img_tree_canopy),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.22f),
            contentScale = ContentScale.Crop
        )

        // Custom Living Tree Canvas drawing Trunk, Roots, Curved Branches & Foliage
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

            // Dimensions and key anchor points
            val trunkBaseX = canvasW * 0.5f
            val trunkBaseY = canvasH * 0.95f
            val trunkMidY = canvasH * 0.70f
            val trunkForkY = canvasH * 0.50f

            // 1. Draw Spreading Ground Earth & Roots
            drawRoots(
                baseX = trunkBaseX,
                baseY = trunkBaseY,
                canvasW = canvasW,
                canvasH = canvasH
            )

            // 2. Draw Stately Tree Trunk
            drawTrunk(
                baseX = trunkBaseX,
                baseY = trunkBaseY,
                midY = trunkMidY,
                forkY = trunkForkY
            )

            // 3. Draw Organic Curved Branches to Each First-Gen Child Node
            if (arrangedChildren.isNotEmpty()) {
                val count = arrangedChildren.size
                arrangedChildren.forEachIndexed { index, child ->
                    val progress = if (count == 1) 0.5f else index.toFloat() / (count - 1)
                    // Compute natural arc position for child node
                    val childX = canvasW * (0.12f + progress * 0.76f)
                    val arcLift = (0.5f - kotlin.math.abs(progress - 0.5f)) * 0.18f
                    val childY = canvasH * (0.28f - arcLift)

                    drawNaturalBranch(
                        startX = trunkBaseX,
                        startY = trunkForkY,
                        endX = childX,
                        endY = childY,
                        isCenter = child.id == oldestChildId
                    )

                    // Draw lush foliage leaf cluster behind node
                    drawLeafCluster(
                        centerX = childX,
                        centerY = childY,
                        isOldest = child.id == oldestChildId
                    )
                }
            }
        }

        // Tree Placard Medallions placed dynamically in transformed Box
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

                // A. ROOT PARENTS on the Tree Trunk Foundation (Single Couple)
                val (rootFather, rootMother) = remember(allMembers, rootParents) {
                    FamilyTreeUtils.findRootParents(if (allMembers.isNotEmpty()) allMembers else rootParents)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = boxHeight * 0.64f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Trunk Founders Badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ForestGreenDark.copy(alpha = 0.92f),
                            shadowElevation = 6.dp,
                            border = BorderStroke(1.5.dp, HeritageGold)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Spa,
                                    contentDescription = null,
                                    tint = HeritageGold,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ROOT PARENTS · TRUNK",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeritageGoldLight,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (rootFather != null) {
                                TreePlaqueMedallion(
                                    member = rootFather,
                                    isOldest = false,
                                    isRoot = true,
                                    allMembers = allMembers,
                                    isLoggedInUser = loggedInMemberId != null && rootFather.id == loggedInMemberId,
                                    onClick = { onMemberClick(rootFather) }
                                )
                            }

                            if (rootFather != null && rootMother != null) {
                                Spacer(modifier = Modifier.width(16.dp))
                                // Sacred Union Heart
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(HeritageGold)
                                        .border(1.5.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = "Union",
                                        tint = ForestGreenDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                            }

                            if (rootMother != null) {
                                TreePlaqueMedallion(
                                    member = rootMother,
                                    isOldest = false,
                                    isRoot = true,
                                    allMembers = allMembers,
                                    isLoggedInUser = loggedInMemberId != null && rootMother.id == loggedInMemberId,
                                    onClick = { onMemberClick(rootMother) }
                                )
                            }
                        }
                    }
                }

                // B. FIRST GENERATION SIBLINGS along the Branches
                if (arrangedChildren.isNotEmpty()) {
                    val count = arrangedChildren.size
                    arrangedChildren.forEachIndexed { index, child ->
                        val progress = if (count == 1) 0.5f else index.toFloat() / (count - 1)
                        val childXFraction = 0.12f + progress * 0.76f
                        val arcLift = (0.5f - kotlin.math.abs(progress - 0.5f)) * 0.18f
                        val childYFraction = 0.28f - arcLift

                        val isOldest = child.id == oldestChildId
                        val isUser = loggedInMemberId != null && child.id == loggedInMemberId

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
                                allMembers = allMembers,
                                isLoggedInUser = isUser,
                                onClick = { onMemberClick(child) }
                            )
                        }
                    }
                } else {
                    // Empty tree prompt
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = boxHeight * 0.25f),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = CreamSurface.copy(alpha = 0.95f),
                            border = BorderStroke(1.5.dp, HeritageGold.copy(alpha = 0.6f)),
                            shadowElevation = 6.dp,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NaturePeople,
                                    contentDescription = null,
                                    tint = ForestGreen,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "The Living Tree Awaits Lineage",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen
                                )
                                Text(
                                    text = "Tap below to add the first children of the root family!",
                                    fontSize = 12.sp,
                                    color = CreamMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onNavigateToAddMember,
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add 1st Generation Branch", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Control Overlay (Zoom In, Zoom Out, Reset, Tree Info Pill)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
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
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset Tree View", modifier = Modifier.size(20.dp))
            }
        }

        // Bottom Tree Legend Tip
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CreamSurface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.3f)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = ForestGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Pinch to zoom · Tap any member to explore their branch",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = BarkBrown
                )
            }
        }
    }
}

/**
 * Plaque Medallion representing a family member on the tree.
 * Styled as a carved wooden/golden plaque with photo and name ribbon.
 * Supports radiant glow and personalized "YOU" badge for the logged-in member.
 */
@Composable
fun TreePlaqueMedallion(
    member: FamilyMember,
    isOldest: Boolean,
    isRoot: Boolean,
    allMembers: List<FamilyMember>,
    isLoggedInUser: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Determine direct children count for sub-branch indicator (shared children of couples)
    val directChildrenCount = remember(member, allMembers) {
        FamilyTreeUtils.findChildrenOf(member, allMembers).size
    }

    // Dynamic glow transition for logged-in user
    val infiniteTransition = rememberInfiniteTransition(label = "medallion_glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    val plaqueSize = when {
        isLoggedInUser && isRoot -> 92.dp
        isLoggedInUser && isOldest -> 94.dp
        isLoggedInUser -> 82.dp
        isRoot -> 80.dp
        isOldest -> 82.dp
        else -> 66.dp
    }

    val borderWidth = when {
        isLoggedInUser -> 3.dp
        isOldest || isRoot -> 2.5.dp
        else -> 1.8.dp
    }

    val borderColor = when {
        isLoggedInUser -> HeritageGold
        isOldest || isRoot -> HeritageGold
        else -> Color(0xFFC8A96E)
    }

    Column(
        modifier = modifier
            .width(plaqueSize + 36.dp)
            .clickable { onClick() }
            .testTag("tree_member_${member.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logged-in Member "YOU" Badge
        if (isLoggedInUser) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = HeritageGold,
                shadowElevation = 6.dp,
                border = BorderStroke(1.2.dp, Color.White),
                modifier = Modifier.padding(bottom = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonPin,
                        contentDescription = "You",
                        tint = ForestGreenDark,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "YOU",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ForestGreenDark,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        } else if (isOldest && !isRoot) {
            // Oldest Child Golden Crown / Leaf Crest
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = HeritageGold,
                shadowElevation = 4.dp,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = ForestGreenDark,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "1ST BORN · ELDEST",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ForestGreenDark,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Circular Medallion Frame with glowing aura halo for logged-in member
        Box(
            contentAlignment = Alignment.Center
        ) {
            if (isLoggedInUser) {
                // Radiant pulsating aura halo
                Box(
                    modifier = Modifier
                        .size(plaqueSize + 16.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    HeritageGold.copy(alpha = 0.6f * glowPulse),
                                    ForestGreen.copy(alpha = 0.28f * glowPulse),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size(plaqueSize)
                    .shadow(
                        elevation = if (isLoggedInUser) 12.dp else if (isOldest || isRoot) 8.dp else 4.dp,
                        shape = CircleShape,
                        ambientColor = if (isLoggedInUser) HeritageGold else Color.Black,
                        spotColor = if (isLoggedInUser) HeritageGold else Color.Black
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isLoggedInUser) {
                                listOf(
                                    Color(0xFFA1887F),
                                    Color(0xFF5D4037),
                                    Color(0xFF3E2723)
                                )
                            } else {
                                listOf(
                                    Color(0xFF8D6E63),
                                    Color(0xFF4E342E),
                                    Color(0xFF3E2723)
                                )
                            }
                        )
                    )
                    .border(borderWidth, borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                MemberAvatar(
                    name = member.fullName,
                    imageUrl = member.profileImageUrl,
                    size = plaqueSize - 8.dp,
                    borderColor = if (isLoggedInUser || isOldest) HeritageGold else Color.Transparent,
                    isRoot = isRoot,
                    backgroundColor = ForestGreenDark
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Name Ribbon Plaque
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isLoggedInUser) ForestGreenDark else if (isOldest || isRoot) Color(0xFF3E2723) else Color(0xFF4E342E),
            border = BorderStroke(if (isLoggedInUser) 1.5.dp else 1.dp, if (isLoggedInUser || isOldest || isRoot) HeritageGold else Color(0xFFBCAAA4)),
            shadowElevation = if (isLoggedInUser) 5.dp else 3.dp,
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isLoggedInUser) "${member.fullName} (You)" else member.fullName,
                    fontSize = if (isLoggedInUser || isOldest || isRoot) 11.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeritageGoldLight,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                if (!member.dateOfBirth.isNullOrBlank() && member.dateOfBirth != "Unknown") {
                    Text(
                        text = member.dateOfBirth ?: "",
                        fontSize = 8.sp,
                        color = if (isLoggedInUser) CreamBackground else CreamMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Sub-branch Badge if person has direct children
        if (directChildrenCount > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ForestGreen,
                border = BorderStroke(0.8.dp, HeritageGold),
                shadowElevation = 2.dp,
                modifier = Modifier.padding(top = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Nature,
                        contentDescription = null,
                        tint = HeritageGold,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$directChildrenCount branch${if (directChildrenCount > 1) "es" else ""}",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ==========================================
// Canvas Drawing Helpers for Natural Living Tree
// ==========================================

private fun DrawScope.drawRoots(
    baseX: Float,
    baseY: Float,
    canvasW: Float,
    canvasH: Float
) {
    val rootColor = TrunkBrown
    val rootBrush = Brush.verticalGradient(
        colors = listOf(TrunkBrown, Color(0xFF3E2723), Color(0xFF2E1C14)),
        startY = baseY - 50f,
        endY = canvasH
    )

    // Left outer root
    val leftPath = Path().apply {
        moveTo(baseX - 40f, baseY - 40f)
        cubicTo(
            baseX - 100f, baseY + 10f,
            baseX - 180f, baseY + 20f,
            canvasW * 0.15f, canvasH + 20f
        )
    }
    drawPath(path = leftPath, brush = rootBrush, style = Stroke(width = 24f, cap = StrokeCap.Round))

    // Right outer root
    val rightPath = Path().apply {
        moveTo(baseX + 40f, baseY - 40f)
        cubicTo(
            baseX + 100f, baseY + 10f,
            baseX + 180f, baseY + 20f,
            canvasW * 0.85f, canvasH + 20f
        )
    }
    drawPath(path = rightPath, brush = rootBrush, style = Stroke(width = 24f, cap = StrokeCap.Round))

    // Center anchor roots
    val centerLeft = Path().apply {
        moveTo(baseX - 15f, baseY - 20f)
        cubicTo(
            baseX - 40f, baseY + 20f,
            baseX - 60f, canvasH - 10f,
            baseX - 80f, canvasH + 30f
        )
    }
    drawPath(path = centerLeft, brush = rootBrush, style = Stroke(width = 18f, cap = StrokeCap.Round))

    val centerRight = Path().apply {
        moveTo(baseX + 15f, baseY - 20f)
        cubicTo(
            baseX + 40f, baseY + 20f,
            baseX + 60f, canvasH - 10f,
            baseX + 80f, canvasH + 30f
        )
    }
    drawPath(path = centerRight, brush = rootBrush, style = Stroke(width = 18f, cap = StrokeCap.Round))
}

private fun DrawScope.drawTrunk(
    baseX: Float,
    baseY: Float,
    midY: Float,
    forkY: Float
) {
    val trunkBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF3E2723), // Dark wood shadow
            Color(0xFF5D4037), // Rich bark
            Color(0xFF795548), // Highlighted grain
            Color(0xFF4E342E)  // Depth edge
        ),
        start = Offset(baseX - 60f, midY),
        end = Offset(baseX + 60f, midY)
    )

    // Solid organic trunk shape
    val trunkBody = Path().apply {
        moveTo(baseX - 55f, baseY)
        cubicTo(
            baseX - 45f, midY + 40f,
            baseX - 40f, forkY + 60f,
            baseX - 35f, forkY
        )
        lineTo(baseX + 35f, forkY)
        cubicTo(
            baseX + 40f, forkY + 60f,
            baseX + 45f, midY + 40f,
            baseX + 55f, baseY
        )
        close()
    }
    drawPath(path = trunkBody, brush = trunkBrush)

    // Bark texture lines
    val textureColor = Color(0xFF27150E).copy(alpha = 0.45f)
    for (i in -2..2) {
        val xOffset = i * 14f
        val texturePath = Path().apply {
            moveTo(baseX + xOffset, baseY - 10f)
            cubicTo(
                baseX + xOffset + 4f, midY + 20f,
                baseX + xOffset - 4f, midY - 20f,
                baseX + (xOffset * 0.7f), forkY + 20f
            )
        }
        drawPath(path = texturePath, color = textureColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawNaturalBranch(
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
    val controlY1 = startY - 80f
    val controlY2 = endY + 80f

    val path = Path().apply {
        moveTo(startX, startY)
        cubicTo(
            startX + deltaX * 0.25f, controlY1,
            endX - deltaX * 0.15f, controlY2,
            endX, endY
        )
    }

    val strokeWidth = if (isCenter) 16f else 12f
    drawPath(
        path = path,
        brush = branchBrush,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )

    // Highlight contour on top of branch
    val highlightPath = Path().apply {
        moveTo(startX, startY - 2f)
        cubicTo(
            startX + deltaX * 0.25f, controlY1 - 2f,
            endX - deltaX * 0.15f, controlY2 - 2f,
            endX, endY - 2f
        )
    }
    drawPath(
        path = highlightPath,
        color = Color(0xFFA1887F).copy(alpha = 0.5f),
        style = Stroke(width = 3f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawLeafCluster(
    centerX: Float,
    centerY: Float,
    isOldest: Boolean
) {
    val radius = if (isOldest) 65f else 50f

    val leafGreenDark = Color(0xFF1B5E20).copy(alpha = 0.65f)
    val leafGreenMid = Color(0xFF2E7D32).copy(alpha = 0.75f)
    val leafGreenLight = Color(0xFF4CAF50).copy(alpha = 0.85f)
    val goldBlossom = HeritageGold.copy(alpha = 0.65f)

    // Base dark foliage shadow
    drawCircle(color = leafGreenDark, radius = radius * 1.1f, center = Offset(centerX, centerY + 8f))

    // Mid layer green leaf bundles
    drawCircle(color = leafGreenMid, radius = radius * 0.9f, center = Offset(centerX - 18f, centerY - 10f))
    drawCircle(color = leafGreenMid, radius = radius * 0.9f, center = Offset(centerX + 18f, centerY - 10f))

    // Bright upper canopy
    drawCircle(color = leafGreenLight, radius = radius * 0.75f, center = Offset(centerX, centerY - 22f))

    // Subtle gold heritage blossom accents
    if (isOldest) {
        drawCircle(color = goldBlossom, radius = 8f, center = Offset(centerX - 24f, centerY - 32f))
        drawCircle(color = goldBlossom, radius = 9f, center = Offset(centerX + 24f, centerY - 32f))
        drawCircle(color = goldBlossom, radius = 10f, center = Offset(centerX, centerY - 42f))
    }
}
