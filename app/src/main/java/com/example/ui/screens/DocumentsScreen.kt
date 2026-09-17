package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FamilyRepository
import com.example.model.FamilyDocument
import com.example.model.FamilyMember
import com.example.ui.components.QadirPrimaryButton
import com.example.ui.components.QadirTopAppBar
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    initialMemberId: String? = null,
    repository: FamilyRepository,
    onBack: () -> Unit
) {
    val documents by repository.documents.collectAsState()
    val members by repository.members.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val categories = listOf("All", "ID Documents", "Education", "Certificates", "Other")
    var selectedCategory by remember { mutableStateOf("All") }
    var showUploadDialog by remember { mutableStateOf(false) }

    fun openOrShareDocument(doc: FamilyDocument) {
        // If Cloudinary URL is available, open online CDN file directly
        if (!doc.url.isNullOrEmpty() && doc.url.startsWith("http")) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(doc.url))
            try {
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // fallback to local uri or share
            }
        }
        if (!doc.localUri.isNullOrEmpty()) {
            val uri = Uri.parse(doc.localUri)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (doc.fileType.equals("PDF", true)) "application/pdf" else "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, doc.title)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(shareIntent, "Share ${doc.title}"))
                } catch (ex: Exception) {
                    Toast.makeText(context, "Opening ${doc.title}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "${doc.title} is recorded in family archives.", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareDocumentLink(doc: FamilyDocument) {
        if (!doc.url.isNullOrEmpty()) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, doc.title)
                putExtra(Intent.EXTRA_TEXT, "Cloudinary document for ${doc.memberName} (${doc.title}):\n${doc.url}")
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Cloudinary Link"))
        } else if (!doc.localUri.isNullOrEmpty()) {
            val uri = Uri.parse(doc.localUri)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, doc.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(Intent.createChooser(shareIntent, "Share ${doc.title}"))
            } catch (e: Exception) {
                Toast.makeText(context, "Could not share file", Toast.LENGTH_SHORT).show()
            }
        } else {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Document Title", doc.title)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Document details copied", Toast.LENGTH_SHORT).show()
        }
    }

    fun syncDocumentToCloudinary(doc: FamilyDocument) {
        coroutineScope.launch {
            Toast.makeText(context, "Uploading ${doc.title} to Cloudinary...", Toast.LENGTH_SHORT).show()
            try {
                if (!doc.localUri.isNullOrEmpty()) {
                    val uri = Uri.parse(doc.localUri)
                    val res = repository.cloudinary.uploadFromUri(uri, doc.fileName)
                    if (res.isSuccess) {
                        res.getOrNull()?.secureUrl?.let { url ->
                            repository.updateDocumentUrl(doc.id, url)
                            Toast.makeText(context, "${doc.title} saved to Cloudinary!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Upload failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // Upload a text summary representation
                    val content = "Qadir Family Archive\nDocument: ${doc.title}\nMember: ${doc.memberName}\nDate: ${doc.date}"
                    val res = repository.cloudinary.uploadBytes(content.toByteArray(), "${doc.id}.txt")
                    if (res.isSuccess) {
                        res.getOrNull()?.secureUrl?.let { url ->
                            repository.updateDocumentUrl(doc.id, url)
                            Toast.makeText(context, "${doc.title} saved to Cloudinary!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Upload failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filteredDocs = documents.filter { doc ->
        val matchesCategory = (selectedCategory == "All" || doc.category == selectedCategory)
        val matchesMember = (initialMemberId == null || doc.memberId == initialMemberId)
        matchesCategory && matchesMember
    }

    val cloudDocsCount = documents.count { it.url != null }

    Scaffold(
        topBar = {
            QadirTopAppBar(
                title = "Documents",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showUploadDialog = true },
                        modifier = Modifier.testTag("btn_upload_document")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Upload Document",
                            tint = ForestGreen
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = CreamSurface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    QadirPrimaryButton(
                        text = "Upload New Document",
                        onClick = { showUploadDialog = true },
                        leadingIcon = Icons.Default.CloudUpload,
                        backgroundColor = ForestGreen
                    )
                }
            }
        },
        containerColor = CreamBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Cloudinary Storage Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreenDark.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ForestGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = ForestGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Cloudinary Storage",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ForestGreenDark
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = HeritageGold.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = repository.cloudinary.cloudName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HeritageGoldDark,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = if (cloudDocsCount == documents.size && documents.isNotEmpty()) {
                                "All $cloudDocsCount documents synced to Cloudinary CDN"
                            } else {
                                "$cloudDocsCount of ${documents.size} documents saved on Cloud CDN"
                            },
                            fontSize = 11.sp,
                            color = BarkBrown.copy(alpha = 0.75f)
                        )
                    }

                    if (documents.any { it.url == null }) {
                        TextButton(
                            onClick = {
                                documents.filter { it.url == null }.forEach { syncDocumentToCloudinary(it) }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Sync All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                        }
                    }
                }
            }

            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = {
                            Text(
                                text = category,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ForestGreen,
                            selectedLabelColor = Color.White,
                            containerColor = CreamSurface,
                            labelColor = BarkBrown
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) ForestGreen else CreamMuted.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = CreamMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No documents found",
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Tap the upload button at the top to save documents to Cloudinary.",
                            color = CreamMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        DocumentItemCard(
                            document = doc,
                            onOpen = { openOrShareDocument(doc) },
                            onShare = { shareDocumentLink(doc) },
                            onSyncToCloud = { syncDocumentToCloudinary(doc) }
                        )
                    }
                }
            }
        }

        // Upload Document Modal Dialog
        if (showUploadDialog) {
            UploadDocumentDialog(
                members = members,
                initialMemberId = initialMemberId,
                repository = repository,
                onDismiss = { showUploadDialog = false }
            )
        }
    }
}

@Composable
private fun DocumentItemCard(
    document: FamilyDocument,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSyncToCloud: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CreamSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Type Badge
            val badgeColor = when (document.fileType.uppercase()) {
                "PDF" -> Color(0xFFE53935)
                "IMG", "JPG", "PNG" -> ForestGreen
                "DOC", "DOCX" -> Color(0xFF1976D2)
                else -> HeritageGoldDark
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (document.fileType.equals("IMG", true) || document.fileType.equals("PNG", true) || document.fileType.equals("JPG", true))
                            Icons.Default.Image else Icons.Default.Description,
                        contentDescription = document.fileType,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = document.fileType.take(4),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = BarkBrown,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${document.fileName}  (${document.fileSize})",
                    fontSize = 12.sp,
                    color = CreamMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${document.date}  ·  ${document.memberName}",
                        fontSize = 11.sp,
                        color = HeritageGoldDark,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (!document.url.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ForestGreen.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = ForestGreen,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Cloudinary",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = HeritageGold.copy(alpha = 0.18f),
                            modifier = Modifier.clickable { onSyncToCloud() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = HeritageGoldDark,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "Sync Cloud",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeritageGoldDark
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(LightLeafGreen)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share or Open Document Link",
                    tint = ForestGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadDocumentDialog(
    members: List<FamilyMember>,
    initialMemberId: String?,
    repository: FamilyRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var selectedMemberId by remember { mutableStateOf(initialMemberId ?: members.firstOrNull()?.id ?: "") }
    var selectedCategory by remember { mutableStateOf("ID Documents") }
    var fileName by remember { mutableStateOf("") }
    var fileType by remember { mutableStateOf("PDF") }
    var fileSize by remember { mutableStateOf("1.2 MB") }
    var selectedFileUri by remember { mutableStateOf<String?>(null) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri.toString()
            val queryCursor = context.contentResolver.query(uri, null, null, null, null)
            queryCursor?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        val realName = cursor.getString(nameIndex)
                        fileName = realName
                        if (title.isBlank()) {
                            title = realName.substringBeforeLast(".")
                        }
                        val ext = realName.substringAfterLast(".", "PDF").uppercase(Locale.US)
                        fileType = if (ext.length <= 4) ext else "DOC"
                    }
                    if (sizeIndex != -1) {
                        val sizeBytes = cursor.getLong(sizeIndex)
                        fileSize = if (sizeBytes > 1024 * 1024) {
                            String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
                        } else {
                            "${sizeBytes / 1024} KB"
                        }
                    }
                }
            }
        }
    }

    val categories = listOf("ID Documents", "Education", "Certificates", "Other")

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = ForestGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload Document", fontWeight = FontWeight.Bold, color = ForestGreen, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Cloudinary Destination Chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ForestGreen.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = ForestGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Destination: Cloudinary Storage (${repository.cloudinary.cloudName})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ForestGreenDark
                        )
                    }
                }

                // File Picker Button
                OutlinedButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isUploading,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedFileUri != null) "Change File (Selected)" else "Choose File from Device")
                }

                // Member selector
                Box {
                    val currentMember = members.find { it.id == selectedMemberId }
                    OutlinedTextField(
                        value = currentMember?.fullName ?: "Select Member",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isUploading,
                        label = { Text("Family Member") },
                        trailingIcon = {
                            IconButton(onClick = { memberDropdownExpanded = true }, enabled = !isUploading) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = memberDropdownExpanded,
                        onDismissRequest = { memberDropdownExpanded = false }
                    ) {
                        members.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.fullName) },
                                onClick = {
                                    selectedMemberId = m.id
                                    memberDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title") },
                    placeholder = { Text("e.g. Passport, CNIC, Degree") },
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    placeholder = { Text("e.g. passport.pdf") },
                    enabled = !isUploading,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selector
                Text("Category:", fontSize = 12.sp, color = CreamMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { if (!isUploading) selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val fName = if (fileName.isNotBlank()) fileName else "${title.lowercase().replace(" ", "_")}.pdf"
                        coroutineScope.launch {
                            isUploading = true
                            try {
                                repository.uploadAndAddDocument(
                                    memberId = selectedMemberId,
                                    title = title,
                                    category = selectedCategory,
                                    fileName = fName,
                                    fileType = fileType,
                                    fileSize = fileSize,
                                    fileUri = selectedFileUri
                                )
                                Toast.makeText(context, "$title uploaded to Cloudinary & saved!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Saved locally ($title)", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } finally {
                                isUploading = false
                            }
                        }
                    }
                },
                enabled = !isUploading && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uploading to Cloud...", fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save to Cloudinary")
                }
            }
        },
        dismissButton = {
            if (!isUploading) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
