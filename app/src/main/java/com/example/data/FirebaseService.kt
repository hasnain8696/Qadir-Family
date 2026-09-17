package com.example.data

import android.content.Context
import android.util.Log
import com.example.model.FamilyDocument
import com.example.model.FamilyMember
import com.example.model.FamilySettings
import com.example.model.PendingRegistration
import com.example.model.UserAccount
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class FirebaseStatus {
    object Uninitialized : FirebaseStatus()
    data class Ready(val projectId: String?) : FirebaseStatus()
    data class OfflineMode(val message: String) : FirebaseStatus()
}

/**
 * Firebase Service responsible for initialization, Firestore persistence,
 * and Firebase Authentication user management.
 */
class FirebaseService private constructor(private val context: Context) {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow<FirebaseStatus>(FirebaseStatus.Uninitialized)
    val status: StateFlow<FirebaseStatus> = _status.asStateFlow()

    private val _currentFirebaseUser = MutableStateFlow<FirebaseUser?>(null)
    val currentFirebaseUser: StateFlow<FirebaseUser?> = _currentFirebaseUser.asStateFlow()

    var isInitialized: Boolean = false
        private set

    init {
        initializeInternal()
    }

    private fun initializeInternal() {
        try {
            val app = if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            } else {
                FirebaseApp.getInstance()
            }

            if (app != null) {
                isInitialized = true
                val projectId = app.options.projectId

                // Configure Firestore with offline cache
                configureFirestoreSettings()

                // Setup Auth listener
                setupAuthListener()

                _status.value = FirebaseStatus.Ready(projectId)
                Log.i(TAG, "Firebase successfully initialized. Project: $projectId")
            } else {
                _status.value = FirebaseStatus.OfflineMode("FirebaseApp initialization returned null")
                Log.w(TAG, "FirebaseApp was null on initialization.")
            }
        } catch (e: Exception) {
            isInitialized = false
            _status.value = FirebaseStatus.OfflineMode("Firebase offline or config pending: ${e.localizedMessage}")
            Log.w(TAG, "Firebase initialization skipped or running in local mode: ${e.message}")
        }
    }

    private fun configureFirestoreSettings() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val cacheSettings = PersistentCacheSettings.newBuilder().build()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(cacheSettings)
                .build()
            firestore.firestoreSettings = settings
            Log.d(TAG, "Firestore offline persistence enabled")
        } catch (e: Exception) {
            Log.w(TAG, "Could not apply Firestore settings: ${e.message}")
        }
    }

    private fun setupAuthListener() {
        try {
            val auth = FirebaseAuth.getInstance()
            _currentFirebaseUser.value = auth.currentUser
            auth.addAuthStateListener { firebaseAuth ->
                _currentFirebaseUser.value = firebaseAuth.currentUser
                Log.d(TAG, "Firebase Auth state changed: ${firebaseAuth.currentUser?.email}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set up AuthStateListener: ${e.message}")
        }
    }

    val auth: FirebaseAuth?
        get() = try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseAuth.getInstance() notice: ${e.message}")
            null
        }

    val firestore: FirebaseFirestore?
        get() = try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseFirestore.getInstance() notice: ${e.message}")
            null
        }

    // ==========================================
    // USER MANAGEMENT & AUTHENTICATION
    // ==========================================

    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        return try {
            val authResult = authInstance.signInWithEmailAndPassword(email.trim(), pass).awaitResult()
            val user = authResult.user ?: return Result.failure(IllegalStateException("Sign in returned null user"))
            _currentFirebaseUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase signInWithEmail notice: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun signUpWithEmail(email: String, pass: String): Result<FirebaseUser> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        return try {
            val authResult = authInstance.createUserWithEmailAndPassword(email.trim(), pass).awaitResult()
            val user = authResult.user ?: return Result.failure(IllegalStateException("Sign up returned null user"))
            _currentFirebaseUser.value = user
            Result.success(user)
        } catch (e: Exception) {
            Log.w(TAG, "Firebase signUpWithEmail notice: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        return try {
            authInstance.sendPasswordResetEmail(email.trim()).awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendVerificationEmail(): Result<Unit> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        val user = authInstance.currentUser ?: return Result.failure(IllegalStateException("No signed-in user found to send verification email."))
        return try {
            user.sendEmailVerification().awaitResult()
            Log.d(TAG, "Firebase verification email sent successfully to ${user.email}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "sendEmailVerification error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun reloadUserAndCheckVerified(): Result<Boolean> {
        val authInstance = auth ?: return Result.failure(IllegalStateException("Firebase Auth is not available"))
        val user = authInstance.currentUser ?: return Result.failure(IllegalStateException("No user currently logged in."))
        return try {
            user.reload().awaitResult()
            val refreshedUser = authInstance.currentUser
            _currentFirebaseUser.value = refreshedUser
            val isVerified = refreshedUser?.isEmailVerified == true
            Log.d(TAG, "User reloaded. isEmailVerified: $isVerified")
            Result.success(isVerified)
        } catch (e: Exception) {
            Log.w(TAG, "reloadUser error: ${e.message}")
            Result.failure(e)
        }
    }

    fun isCurrentUserEmailVerified(): Boolean {
        val user = auth?.currentUser
        return user?.isEmailVerified == true
    }

    suspend fun verifyAndResetPassword(email: String, cnic: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val snapshot = db.collection(COLLECTION_MEMBERS)
                .whereEqualTo("email", email.trim())
                .whereEqualTo("cnic", cnic.trim())
                .get()
                .awaitResult()

            if (!snapshot.isEmpty) {
                val resetResult = sendPasswordReset(email.trim())
                if (resetResult.isFailure) {
                    return resetResult
                }
            }
            // Always return success even if not found to prevent user enumeration
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
            _currentFirebaseUser.value = null
        } catch (e: Exception) {
            Log.w(TAG, "Error signing out from Firebase: ${e.message}")
        }
    }

    // ==========================================
    // FIRESTORE DATA PERSISTENCE
    // ==========================================

    suspend fun fetchUserRole(uid: String): Result<String> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val doc = db.collection(COLLECTION_USERS).document(uid).get().awaitResult()
            if (doc.exists()) {
                val role = doc.getString("role") ?: "member"
                Result.success(role)
            } else {
                Result.success("member")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveUserRole(uid: String, role: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available for project qadir-family"))
        return try {
            val map = hashMapOf("role" to role, "updatedAt" to System.currentTimeMillis())
            db.collection(COLLECTION_USERS)
                .document(uid)
                .set(map, SetOptions.merge())
                .awaitResult()
            Log.d(TAG, "Updated role '$role' for user $uid in Firestore users collection")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user role in Firestore users/$uid: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun checkAdminClaims(forceRefresh: Boolean = false): Result<Boolean> {
        val user = auth?.currentUser ?: return Result.success(false)
        return try {
            val tokenResult = user.getIdToken(forceRefresh).awaitResult()
            val claims = tokenResult.claims
            val isAdmin = claims["admin"] == true || claims["role"] == "admin"
            Result.success(isAdmin)
        } catch (e: Exception) {
            Log.w(TAG, "Could not check admin claims: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun fetchUserAccount(uid: String): Result<UserAccount?> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val doc = db.collection(COLLECTION_USERS).document(uid).get().awaitResult()
            if (doc.exists()) {
                val account = UserAccount(
                    uid = uid,
                    email = doc.getString("email") ?: "",
                    fullName = doc.getString("fullName") ?: "",
                    phone = doc.getString("phone") ?: "",
                    role = doc.getString("role") ?: "member",
                    memberId = doc.getString("memberId").takeIf { !it.isNullOrBlank() },
                    isEmailVerified = false,
                    isApproved = doc.getBoolean("isApproved") ?: false
                )
                Result.success(account)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveUserDocument(
        uid: String,
        email: String,
        fullName: String,
        phone: String = "",
        role: String = "member",
        memberId: String? = null,
        isApproved: Boolean = false
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available for project qadir-family"))
        return try {
            val map = hashMapOf(
                "uid" to uid,
                "email" to email.trim(),
                "fullName" to fullName.trim(),
                "phone" to phone.trim(),
                "role" to role,
                "memberId" to (memberId ?: ""),
                "isApproved" to isApproved,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_USERS)
                .document(uid)
                .set(map, SetOptions.merge())
                .awaitResult()
            Log.d(TAG, "User document successfully saved in Firestore users/$uid")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user document in Firestore users/$uid: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun saveMember(member: FamilyMember): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available for project qadir-family"))
        return try {
            val map = memberToMap(member)
            db.collection(COLLECTION_MEMBERS)
                .document(member.id)
                .set(map, SetOptions.merge())
                .awaitResult()
            Log.d(TAG, "Member document successfully saved in Firestore members/${member.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving member in Firestore members/${member.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun syncAllMembers(members: List<FamilyMember>): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val batch = db.batch()
            for (member in members) {
                val docRef = db.collection(COLLECTION_MEMBERS).document(member.id)
                batch.set(docRef, memberToMap(member), SetOptions.merge())
            }
            batch.commit().awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMembers(): Result<List<FamilyMember>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val snapshot = db.collection(COLLECTION_MEMBERS).get().awaitResult()
            val list = snapshot.documents.mapNotNull { doc -> mapToMember(doc) }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMember(memberId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            db.collection(COLLECTION_MEMBERS).document(memberId).delete().awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveDocument(document: FamilyDocument): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val map = hashMapOf(
                "id" to document.id,
                "memberId" to document.memberId,
                "memberName" to document.memberName,
                "title" to document.title,
                "category" to document.category,
                "fileName" to document.fileName,
                "fileType" to document.fileType,
                "fileSize" to document.fileSize,
                "date" to document.date,
                "url" to document.url,
                "localUri" to document.localUri,
                "uploadedBy" to document.uploadedBy
            )
            db.collection(COLLECTION_DOCUMENTS)
                .document(document.id)
                .set(map, SetOptions.merge())
                .awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDocuments(): Result<List<FamilyDocument>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val snapshot = db.collection(COLLECTION_DOCUMENTS).get().awaitResult()
            val list = snapshot.documents.mapNotNull { doc -> mapToFamilyDocument(doc) }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDocument(documentId: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            db.collection(COLLECTION_DOCUMENTS).document(documentId).delete().awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveSettings(settings: FamilySettings): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            // Write public config to settings/family_config (Strictly WITHOUT familyPasscode)
            val publicMap = hashMapOf(
                "familyName" to settings.familyName,
                "tagline" to settings.tagline,
                "rootFatherId" to settings.rootFatherId,
                "rootMotherId" to settings.rootMotherId
            )
            db.collection(COLLECTION_SETTINGS)
                .document("family_config")
                .set(publicMap, SetOptions.merge())
                .awaitResult()

            // If passcode is provided and admin is logged in, write passcode to secure backend document
            if (settings.familyPasscode.isNotBlank()) {
                val secretMap = hashMapOf(
                    "familyPasscode" to settings.familyPasscode,
                    "updatedAt" to System.currentTimeMillis()
                )
                try {
                    db.collection(COLLECTION_SETTINGS)
                        .document("secrets")
                        .set(secretMap, SetOptions.merge())
                        .awaitResult()
                } catch (e: Exception) {
                    Log.d(TAG, "Secrets doc write notice: ${e.message}")
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePasscodeSecret(newPasscode: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val secretMap = hashMapOf(
                "familyPasscode" to newPasscode,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_SETTINGS)
                .document("secrets")
                .set(secretMap, SetOptions.merge())
                .awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving passcode secret: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchSettings(isAdmin: Boolean = false): Result<FamilySettings?> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val doc = db.collection(COLLECTION_SETTINGS).document("family_config").get().awaitResult()
            if (doc.exists()) {
                var passcode = ""
                if (isAdmin) {
                    try {
                        val secretDoc = db.collection(COLLECTION_SETTINGS).document("secrets").get().awaitResult()
                        if (secretDoc.exists()) {
                            passcode = secretDoc.getString("familyPasscode") ?: ""
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Could not fetch secrets doc: ${e.message}")
                    }
                }
                val settings = FamilySettings(
                    familyPasscode = passcode,
                    familyName = doc.getString("familyName") ?: "My Family",
                    tagline = doc.getString("tagline") ?: "One Family · One Tree · Forever",
                    rootFatherId = doc.getString("rootFatherId") ?: "",
                    rootMotherId = doc.getString("rootMotherId") ?: ""
                )
                Result.success(settings)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // REAL-TIME FIRESTORE LISTENERS
    // ==========================================

    fun startMembersListener(onUpdate: (List<FamilyMember>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection(COLLECTION_MEMBERS).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Members snapshot listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val members = snapshot.documents.mapNotNull { doc -> mapToMember(doc) }
                    Log.d(TAG, "Real-time members update: ${members.size} members from Firestore")
                    onUpdate(members)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach members snapshot listener: ${e.message}")
            null
        }
    }

    fun startDocumentsListener(onUpdate: (List<FamilyDocument>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection(COLLECTION_DOCUMENTS).addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Documents snapshot listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val docs = snapshot.documents.mapNotNull { doc -> mapToFamilyDocument(doc) }
                    Log.d(TAG, "Real-time documents update: ${docs.size} documents from Firestore")
                    onUpdate(docs)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach documents snapshot listener: ${e.message}")
            null
        }
    }

    fun startSettingsListener(onUpdate: (FamilySettings) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection(COLLECTION_SETTINGS).document("family_config").addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Settings snapshot listener error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val settings = FamilySettings(
                        familyPasscode = "",
                        familyName = snapshot.getString("familyName") ?: "My Family",
                        tagline = snapshot.getString("tagline") ?: "One Family · One Tree · Forever",
                        rootFatherId = snapshot.getString("rootFatherId") ?: "",
                        rootMotherId = snapshot.getString("rootMotherId") ?: ""
                    )
                    onUpdate(settings)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach settings snapshot listener: ${e.message}")
            null
        }
    }

    /**
     * Finds a member globally in Firestore by accountUid, email, or phone.
     */
    suspend fun findMemberInFirestore(uid: String?, email: String?, phone: String?): Result<FamilyMember?> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            if (!uid.isNullOrBlank()) {
                val uidSnap = db.collection(COLLECTION_MEMBERS).whereEqualTo("accountUid", uid).get().awaitResult()
                val found = uidSnap.documents.firstOrNull()?.let { mapToMember(it) }
                if (found != null) return Result.success(found)
            }
            if (!email.isNullOrBlank()) {
                val emailSnap = db.collection(COLLECTION_MEMBERS).whereEqualTo("email", email.trim()).get().awaitResult()
                val found = emailSnap.documents.firstOrNull()?.let { mapToMember(it) }
                if (found != null) return Result.success(found)
            }
            if (!phone.isNullOrBlank()) {
                val phoneClean = phone.replace(" ", "").replace("-", "")
                val allDocs = db.collection(COLLECTION_MEMBERS).get().awaitResult()
                val found = allDocs.documents.mapNotNull { mapToMember(it) }.find {
                    it.phone != null && it.phone.replace(" ", "").replace("-", "") == phoneClean
                }
                if (found != null) return Result.success(found)
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // SENSITIVE RECORDS & PENDING REGISTRATIONS
    // ==========================================

    suspend fun saveSensitiveRecord(memberId: String, accountUid: String?, cnic: String): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val map = hashMapOf(
                "memberId" to memberId,
                "accountUid" to (accountUid ?: ""),
                "cnic" to cnic,
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection(COLLECTION_SENSITIVE_RECORDS).document(memberId).set(map, SetOptions.merge()).awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving sensitive record: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchSensitiveRecord(memberId: String): Result<String?> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val doc = db.collection(COLLECTION_SENSITIVE_RECORDS).document(memberId).get().awaitResult()
            Result.success(doc.getString("cnic"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveRegistration(reg: PendingRegistration): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val map = hashMapOf(
                "id" to reg.id,
                "uid" to reg.uid,
                "fullName" to reg.fullName,
                "email" to reg.email,
                "phone" to reg.phone,
                "gender" to reg.gender,
                "dateOfBirth" to reg.dateOfBirth,
                "fatherName" to reg.fatherName,
                "selectedFatherId" to (reg.selectedFatherId ?: ""),
                "motherName" to reg.motherName,
                "selectedMotherId" to (reg.selectedMotherId ?: ""),
                "passcode" to reg.passcode,
                "cnic" to (reg.cnic ?: ""),
                "isRootParent" to reg.isRootParent,
                "status" to reg.status,
                "rejectionReason" to (reg.rejectionReason ?: ""),
                "createdAt" to reg.createdAt
            )
            db.collection(COLLECTION_REGISTRATIONS).document(reg.id).set(map).awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving registration: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchPendingRegistrations(): Result<List<PendingRegistration>> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val snap = db.collection(COLLECTION_REGISTRATIONS)
                .whereEqualTo("status", "pending")
                .get()
                .awaitResult()
            val list = snap.documents.mapNotNull { doc -> mapToPendingRegistration(doc) }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startRegistrationsListener(onUpdate: (List<PendingRegistration>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection(COLLECTION_REGISTRATIONS)
                .whereEqualTo("status", "pending")
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.w(TAG, "Registrations listener error: ${err.message}")
                        return@addSnapshotListener
                    }
                    if (snap != null) {
                        val list = snap.documents.mapNotNull { doc -> mapToPendingRegistration(doc) }
                        onUpdate(list)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach registrations listener: ${e.message}")
            null
        }
    }

    fun startUserAccountListener(
        uid: String,
        onUpdate: (isApproved: Boolean, memberId: String?, role: String) -> Unit
    ): ListenerRegistration? {
        val db = firestore ?: return null
        return try {
            db.collection(COLLECTION_USERS).document(uid).addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                val isApproved = snap.getBoolean("isApproved") ?: false
                val memberId = snap.getString("memberId")
                val role = snap.getString("role") ?: "member"
                onUpdate(isApproved, memberId, role)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun approveRegistrationTransactional(
        registration: PendingRegistration,
        resolvedMember: FamilyMember
    ): Result<Unit> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available"))
        return try {
            val batch = db.batch()

            // 1. Permanent Member document (Strictly WITHOUT CNIC)
            val memberRef = db.collection(COLLECTION_MEMBERS).document(resolvedMember.id)
            batch.set(memberRef, memberToMap(resolvedMember), SetOptions.merge())

            // 2. Sensitive record for CNIC if provided
            if (!registration.cnic.isNullOrBlank()) {
                val sensitiveRef = db.collection(COLLECTION_SENSITIVE_RECORDS).document(resolvedMember.id)
                batch.set(sensitiveRef, hashMapOf(
                    "memberId" to resolvedMember.id,
                    "accountUid" to registration.uid,
                    "cnic" to registration.cnic,
                    "updatedAt" to System.currentTimeMillis()
                ), SetOptions.merge())
            }

            // 3. Update User Document with approval and linked memberId
            val userRef = db.collection(COLLECTION_USERS).document(registration.uid)
            batch.set(userRef, hashMapOf(
                "memberId" to resolvedMember.id,
                "isApproved" to true,
                "role" to "member",
                "updatedAt" to System.currentTimeMillis()
            ), SetOptions.merge())

            // 4. Update Registration Status
            val regRef = db.collection(COLLECTION_REGISTRATIONS).document(registration.id)
            batch.update(regRef, mapOf(
                "status" to "approved",
                "memberId" to resolvedMember.id,
                "resolvedGeneration" to resolvedMember.generation,
                "updatedAt" to System.currentTimeMillis()
            ))

            // 5. Update parent's children list in Firestore
            val parentIds = listOfNotNull(resolvedMember.fatherId, resolvedMember.motherId)
            for (pId in parentIds) {
                val pRef = db.collection(COLLECTION_MEMBERS).document(pId)
                batch.update(pRef, "childrenIds", FieldValue.arrayUnion(resolvedMember.id))
            }

            batch.commit().awaitResult()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error approving registration: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Server-side transactional registration ensuring global constraints
     * (e.g. max 2 Root Parents globally in the family tree).
     * Atomic transaction writes the member record and claims root slot in family_config.
     */
    suspend fun registerMemberTransactional(
        newMember: FamilyMember,
        isRootParent: Boolean,
        registrationId: String? = null
    ): Result<FamilyMember> {
        val db = firestore ?: return Result.failure(IllegalStateException("Firestore is not available for project qadir-family"))
        if (!isRootParent) {
            return Result.failure(IllegalStateException("Normal members must use /registrations rather than direct tree creation."))
        }
        return try {
            val registeredMember = db.runTransaction { tx ->
                val settingsRef = db.collection(COLLECTION_SETTINGS).document("family_config")
                val memberRef = db.collection(COLLECTION_MEMBERS).document(newMember.id)

                val settingsSnap = tx.get(settingsRef)
                val existingRootFatherId = if (settingsSnap.exists()) settingsSnap.getString("rootFatherId") ?: "" else ""
                val existingRootMotherId = if (settingsSnap.exists()) settingsSnap.getString("rootMotherId") ?: "" else ""

                val isMale = newMember.gender.equals("Male", ignoreCase = true)
                if (isMale && existingRootFatherId.isNotEmpty() && existingRootFatherId != newMember.id) {
                    throw IllegalStateException("A Root Father is already registered on the family tree trunk.")
                }
                if (!isMale && existingRootMotherId.isNotEmpty() && existingRootMotherId != newMember.id) {
                    throw IllegalStateException("A Root Mother is already registered on the family tree trunk.")
                }
                if (existingRootFatherId.isNotEmpty() && existingRootMotherId.isNotEmpty() &&
                    existingRootFatherId != newMember.id && existingRootMotherId != newMember.id) {
                    throw IllegalStateException("Maximum of 2 Root Parents are already registered in the family tree. A third root parent is strictly disallowed.")
                }

                val otherRootId = if (isMale) existingRootMotherId else existingRootFatherId
                val memberToSave = if (otherRootId.isNotEmpty()) {
                    newMember.copy(spouseId = otherRootId)
                } else {
                    newMember
                }

                // 1. Write the member's own record (Excluding CNIC)
                tx.set(memberRef, memberToMap(memberToSave), SetOptions.merge())

                // 2. Claim the root parent slot in family_config settings (Strictly WITHOUT familyPasscode)
                val settingsUpdate = if (settingsSnap.exists()) {
                    if (isMale) {
                        mapOf("rootFatherId" to newMember.id)
                    } else {
                        mapOf("rootMotherId" to newMember.id)
                    }
                } else {
                    mapOf(
                        "familyName" to "Qadir Family",
                        "tagline" to "One Family · One Tree · Forever",
                        "rootFatherId" to if (isMale) newMember.id else "",
                        "rootMotherId" to if (!isMale) newMember.id else ""
                    )
                }
                tx.set(settingsRef, settingsUpdate, SetOptions.merge())

                // 3. Link existing other root parent's spouseId
                if (otherRootId.isNotEmpty()) {
                    val otherMemberRef = db.collection(COLLECTION_MEMBERS).document(otherRootId)
                    tx.update(otherMemberRef, "spouseId", newMember.id)
                }

                // 4. Save CNIC to sensitive_records if provided
                if (!newMember.cnic.isNullOrBlank()) {
                    val sensitiveRef = db.collection(COLLECTION_SENSITIVE_RECORDS).document(newMember.id)
                    tx.set(sensitiveRef, hashMapOf(
                        "memberId" to newMember.id,
                        "accountUid" to (newMember.accountUid ?: ""),
                        "cnic" to newMember.cnic,
                        "updatedAt" to System.currentTimeMillis()
                    ), SetOptions.merge())
                }

                // 5. Update and link user account document if accountUid is present
                if (!newMember.accountUid.isNullOrBlank()) {
                    val userRef = db.collection(COLLECTION_USERS).document(newMember.accountUid)
                    tx.set(userRef, hashMapOf(
                        "memberId" to newMember.id,
                        "isApproved" to true,
                        "role" to "member",
                        "updatedAt" to System.currentTimeMillis()
                    ), SetOptions.merge())
                }

                // 6. Update registration document if this root was approved from pending registrations
                if (!registrationId.isNullOrBlank()) {
                    val regRef = db.collection(COLLECTION_REGISTRATIONS).document(registrationId)
                    tx.update(regRef, mapOf(
                        "status" to "approved",
                        "memberId" to newMember.id,
                        "resolvedGeneration" to 0,
                        "updatedAt" to System.currentTimeMillis()
                    ))
                }

                memberToSave
            }.awaitResult()

            Result.success(registeredMember)
        } catch (e: Exception) {
            Log.e(TAG, "Transactional registration error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==========================================
    // MAPPING HELPERS
    // ==========================================

    private fun memberToMap(member: FamilyMember): HashMap<String, Any?> {
        return hashMapOf(
            "id" to member.id,
            "fullName" to member.fullName,
            "relation" to member.relation,
            "generation" to member.generation,
            "gender" to member.gender,
            "fatherId" to member.fatherId,
            "motherId" to member.motherId,
            "spouseId" to member.spouseId,
            "childrenIds" to member.childrenIds,
            // Sensitive data like CNIC is strictly stored in /sensitive_records, never in /members
            "phone" to member.phone,
            "email" to member.email,
            "city" to member.city,
            "dateOfBirth" to member.dateOfBirth,
            "about" to member.about,
            "profileImageUrl" to member.profileImageUrl,
            "accountUid" to member.accountUid,
            "createdAt" to member.createdAt,
            "isApproved" to member.isApproved
        )
    }

    private fun mapToPendingRegistration(doc: DocumentSnapshot): PendingRegistration? {
        val id = doc.getString("id") ?: doc.id
        val uid = doc.getString("uid") ?: return null
        val fullName = doc.getString("fullName") ?: "Applicant"
        val email = doc.getString("email") ?: ""
        val phone = doc.getString("phone") ?: ""
        val gender = doc.getString("gender") ?: "Male"
        val dateOfBirth = doc.getString("dateOfBirth") ?: "01-01-2000"
        val fatherName = doc.getString("fatherName") ?: ""
        val selectedFatherId = doc.getString("selectedFatherId")
        val motherName = doc.getString("motherName") ?: ""
        val selectedMotherId = doc.getString("selectedMotherId")
        val passcode = doc.getString("passcode") ?: ""
        val cnic = doc.getString("cnic")
        val isRootParent = doc.getBoolean("isRootParent") ?: false
        val status = doc.getString("status") ?: "pending"
        val rejectionReason = doc.getString("rejectionReason")
        val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

        return PendingRegistration(
            id = id,
            uid = uid,
            fullName = fullName,
            email = email,
            phone = phone,
            gender = gender,
            dateOfBirth = dateOfBirth,
            fatherName = fatherName,
            selectedFatherId = selectedFatherId,
            motherName = motherName,
            selectedMotherId = selectedMotherId,
            passcode = passcode,
            cnic = cnic,
            isRootParent = isRootParent,
            status = status,
            rejectionReason = rejectionReason,
            createdAt = createdAt
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToMember(doc: DocumentSnapshot): FamilyMember? {
        val id = doc.getString("id") ?: doc.id
        val fullName = doc.getString("fullName") ?: return null
        val relation = doc.getString("relation") ?: "Family Member"
        val generation = doc.getLong("generation")?.toInt() ?: 1
        val gender = doc.getString("gender") ?: "Male"
        val childrenIds = (doc.get("childrenIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        return FamilyMember(
            id = id,
            fullName = fullName,
            relation = relation,
            generation = generation,
            gender = gender,
            fatherId = doc.getString("fatherId"),
            motherId = doc.getString("motherId"),
            spouseId = doc.getString("spouseId"),
            childrenIds = childrenIds,
            cnic = doc.getString("cnic"),
            phone = doc.getString("phone"),
            email = doc.getString("email"),
            city = doc.getString("city") ?: "Lahore",
            dateOfBirth = doc.getString("dateOfBirth"),
            about = doc.getString("about"),
            profileImageUrl = doc.getString("profileImageUrl"),
            accountUid = doc.getString("accountUid"),
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            isApproved = doc.getBoolean("isApproved") ?: true
        )
    }

    private fun mapToFamilyDocument(doc: DocumentSnapshot): FamilyDocument? {
        val id = doc.getString("id") ?: doc.id
        val memberId = doc.getString("memberId") ?: return null
        val title = doc.getString("title") ?: return null
        return FamilyDocument(
            id = id,
            memberId = memberId,
            memberName = doc.getString("memberName") ?: "Family Member",
            title = title,
            category = doc.getString("category") ?: "ID Documents",
            fileName = doc.getString("fileName") ?: "document.pdf",
            fileType = doc.getString("fileType") ?: "PDF",
            fileSize = doc.getString("fileSize") ?: "1.0 MB",
            date = doc.getString("date") ?: "Recent",
            url = doc.getString("url"),
            localUri = doc.getString("localUri"),
            uploadedBy = doc.getString("uploadedBy") ?: "Family Member"
        )
    }

    companion object {
        private const val TAG = "FirebaseService"
        private const val COLLECTION_MEMBERS = "members"
        private const val COLLECTION_DOCUMENTS = "documents"
        private const val COLLECTION_SETTINGS = "settings"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_REGISTRATIONS = "registrations"
        private const val COLLECTION_SENSITIVE_RECORDS = "sensitive_records"

        @Volatile
        private var instance: FirebaseService? = null

        fun getInstance(context: Context): FirebaseService {
            return instance ?: synchronized(this) {
                instance ?: FirebaseService(context.applicationContext).also { instance = it }
            }
        }

        fun initialize(context: Context): FirebaseService {
            return getInstance(context)
        }
    }
}

/**
 * Extension helper to await Task<T> without external dependency on play-services coroutines.
 */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        if (cont.isActive) cont.resume(result)
    }
    addOnFailureListener { exception ->
        if (cont.isActive) cont.resumeWithException(exception)
    }
    addOnCanceledListener {
        if (cont.isActive) cont.cancel()
    }
}
