package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.theme.*
import com.example.util.FamilyTreeUtils
import kotlinx.coroutines.launch

@Composable
fun MemberProfileScreen(
    memberId: String,
    repository: FamilyRepository,
    onNavigateToDocuments: (String) -> Unit,
    onNavigateToMember: ((String) -> Unit)? = null,
    onBack: () -> Unit
) {
    val members by repository.members.collectAsState()
    val member = members.find { it.id == memberId } ?: members.firstOrNull()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && member != null) {
            repository.updateMember(member.copy(profileImageUrl = uri.toString()))
            Toast.makeText(context, "Photo updated. Backing up...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                try {
                    val res = repository.cloudinary.uploadFromUri(
                        uri = uri,
                        fileName = "${member.id}_profile.jpg",
                        folder = "qadir_family/profiles"
                    )
                    res.getOrNull()?.secureUrl?.let { cloudUrl ->
                        repository.updateMember(member.copy(profileImageUrl = cloudUrl))
                    }
                } catch (e: Exception) {
                    // Local photo remains
                }
            }
        }
    }

    if (member == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Member not found.")
        }
        return
    }

    val (father, mother) = FamilyTreeUtils.findParentsOf(member, members)
    val spouse = FamilyTreeUtils.findSpouseOf(member, members)
    val children = FamilyTreeUtils.findChildrenOf(member, members)

    Scaffold(
        topBar = {
            QadirTopAppBar(
                title = "Member Profile",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        val shareText = "Qadir Family Heritage\nName: ${member.fullName}\nRelation: ${member.relation}\nCity: ${member.city ?: "N/A"}\nPhone: ${member.phone ?: "N/A"}"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Profile"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = ForestGreen)
                    }
                }
            )
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
            // Member Avatar Header with natural circular wooden/gold ring
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(LightLeafGreen, ForestGreen.copy(alpha = 0.2f))
                        )
                    )
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                MemberAvatar(
                    name = member.fullName,
                    imageUrl = member.profileImageUrl,
                    size = 110.dp,
                    borderColor = HeritageGold,
                    isRoot = member.generation == 0,
                    backgroundColor = ForestGreen
                )

                // Camera icon overlay badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 4.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ForestGreen)
                        .border(1.5.dp, HeritageGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Change Photo",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = member.fullName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreen,
                fontFamily = FontFamily.Serif
            )

            // Generation Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (member.generation == 0) HeritageGold else ForestGreen,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    text = if (member.generation == 0) "Trunk Founder · Root Parent" else "Generation ${member.generation}",
                    color = if (member.generation == 0) BarkBrown else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4 Info Chips (Gender, Relation, Date of Birth, City)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoChip(
                    icon = if (member.gender == "Female") Icons.Default.Female else Icons.Default.Male,
                    label = "Gender",
                    value = member.gender ?: "Male",
                    modifier = Modifier.weight(1f)
                )
                InfoChip(
                    icon = Icons.Default.FamilyRestroom,
                    label = "Relation",
                    value = member.relation,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InfoChip(
                    icon = Icons.Default.CalendarToday,
                    label = "Date of Birth",
                    value = member.dateOfBirth ?: "Not specified",
                    modifier = Modifier.weight(1f)
                )
                InfoChip(
                    icon = Icons.Default.LocationOn,
                    label = "City",
                    value = member.city ?: "Lahore",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lineage Links: Parents / Spouse / Children
            if (father != null || mother != null || spouse != null || children.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CreamSurface),
                    border = BorderStroke(1.dp, HeritageGold.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Family Lineage Connections",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )

                        if (father != null) {
                            LineageRow(
                                title = "Father",
                                personName = father.fullName,
                                onClick = { onNavigateToMember?.invoke(father.id) }
                            )
                        }
                        if (mother != null) {
                            LineageRow(
                                title = "Mother",
                                personName = mother.fullName,
                                onClick = { onNavigateToMember?.invoke(mother.id) }
                            )
                        }
                        if (spouse != null) {
                            LineageRow(
                                title = "Spouse",
                                personName = spouse.fullName,
                                onClick = { onNavigateToMember?.invoke(spouse.id) }
                            )
                        }
                        if (children.isNotEmpty()) {
                            Text(
                                text = "Children (${children.size}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BarkBrown
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                children.forEach { child ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = LightLeafGreen,
                                        border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.4f)),
                                        modifier = Modifier.clickable { onNavigateToMember?.invoke(child.id) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            MemberAvatar(name = child.fullName, imageUrl = child.profileImageUrl, size = 24.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = child.fullName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = ForestGreenDark
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // About Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CreamSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = member.about ?: "A dedicated member of the Qadir Family lineage.",
                        fontSize = 13.sp,
                        color = BarkBrown,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Contact Info Card (Phone & Email)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CreamSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Contact & Reachability",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                member.phone?.let {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$it"))
                                    context.startActivity(intent)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(LightLeafGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Phone", fontSize = 11.sp, color = CreamMuted)
                            Text(
                                text = member.phone ?: "Not provided",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BarkBrown
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                member.email?.let {
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$it"))
                                    context.startActivity(intent)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(LightLeafGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mail, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Email", fontSize = 11.sp, color = CreamMuted)
                            Text(
                                text = member.email ?: "Not provided",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BarkBrown
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Prominent "View Documents" Action
            QadirPrimaryButton(
                text = "View Documents",
                onClick = { onNavigateToDocuments(member.id) },
                leadingIcon = Icons.Default.Description,
                backgroundColor = ForestGreen
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LineageRow(
    title: String,
    personName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$title:", fontSize = 12.sp, color = CreamMuted, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = personName, fontSize = 13.sp, color = ForestGreen, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun InfoChip(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CreamSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ForestGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BarkBrown,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = CreamMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
