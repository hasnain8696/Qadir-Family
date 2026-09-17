package com.example.model

data class FamilyMember(
    val id: String,
    val fullName: String,
    val relation: String, // "Grand Father", "Grand Mother", "Son", "Daughter", "Grandson", "Granddaughter", etc.
    val generation: Int, // 0 = Root Parents, 1 = 1st Gen, 2 = 2nd Gen, 3 = 3rd Gen
    val gender: String = "Male", // "Male" or "Female"
    val fatherId: String? = null,
    val motherId: String? = null,
    val spouseId: String? = null,
    val childrenIds: List<String> = emptyList(),
    val cnic: String? = null, // Masked for privacy
    val phone: String? = null,
    val email: String? = null,
    val city: String? = "Lahore",
    val dateOfBirth: String? = null,
    val about: String? = null,
    val profileImageUrl: String? = null,
    val accountUid: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isApproved: Boolean = true
)

data class FamilyDocument(
    val id: String,
    val memberId: String,
    val memberName: String,
    val title: String,
    val category: String, // "ID Documents", "Education", "Certificates", "Other"
    val fileName: String,
    val fileType: String = "PDF",
    val fileSize: String = "1.5 MB",
    val date: String = "Recent",
    val url: String? = null,
    val localUri: String? = null,
    val uploadedBy: String = "Family Admin"
)

data class UserAccount(
    val uid: String,
    val email: String,
    val fullName: String,
    val phone: String,
    val role: String, // "family" or "admin"
    val memberId: String? = null,
    val isEmailVerified: Boolean = false,
    val isApproved: Boolean = false
)

data class FamilySettings(
    val familyPasscode: String = "",
    val familyName: String = "My Family",
    val tagline: String = "One Family · One Tree · Forever",
    val rootFatherId: String = "",
    val rootMotherId: String = ""
)

data class PendingRegistration(
    val id: String,
    val uid: String,
    val fullName: String,
    val email: String,
    val phone: String,
    val gender: String = "Male",
    val dateOfBirth: String = "01-01-2000",
    val fatherName: String = "",
    val selectedFatherId: String? = null,
    val motherName: String = "",
    val selectedMotherId: String? = null,
    val passcode: String = "",
    val cnic: String? = null,
    val isRootParent: Boolean = false,
    val status: String = "pending", // "pending", "approved", "rejected"
    val rejectionReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

object SeedData {
    val initialMembers = emptyList<FamilyMember>()

    val initialDocuments = emptyList<FamilyDocument>()
}
