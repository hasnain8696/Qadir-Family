package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.example.model.FamilyDocument
import com.example.model.FamilyMember
import com.example.model.FamilySettings
import com.example.model.PendingRegistration
import com.example.model.SeedData
import com.example.model.UserAccount
import com.example.util.FamilyTreeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class FamilyRepository(context: Context) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val firebase: FirebaseService = FirebaseService.getInstance(context)
    val cloudinary: CloudinaryService = CloudinaryService.getInstance(context)

    private val prefs: SharedPreferences =
        context.getSharedPreferences("qadir_family_prefs", Context.MODE_PRIVATE)

    private val _members = MutableStateFlow<List<FamilyMember>>(emptyList())
    val members: StateFlow<List<FamilyMember>> = _members.asStateFlow()

    private val _documents = MutableStateFlow<List<FamilyDocument>>(emptyList())
    val documents: StateFlow<List<FamilyDocument>> = _documents.asStateFlow()

    private val _settings = MutableStateFlow(FamilySettings())
    val settings: StateFlow<FamilySettings> = _settings.asStateFlow()

    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    private val _pendingRegistrations = MutableStateFlow<List<PendingRegistration>>(emptyList())
    val pendingRegistrations: StateFlow<List<PendingRegistration>> = _pendingRegistrations.asStateFlow()

    fun startUserSync(uid: String) {
        if (!firebase.isInitialized) return
        firebase.startUserAccountListener(uid) { isApproved, memberId, role ->
            val cur = _currentUser.value ?: return@startUserAccountListener
            if (cur.uid == uid) {
                val updated = cur.copy(
                    isApproved = isApproved,
                    memberId = memberId ?: cur.memberId,
                    role = role
                )
                _currentUser.value = updated
                persistUser(updated)
            }
        }
    }

    init {
        // Schema version migration to ensure old fake members are removed
        val dataVersion = prefs.getInt("data_schema_version", 0)
        if (dataVersion < 5) {
            prefs.edit()
                .putInt("data_schema_version", 5)
                .remove("members_json")
                .remove("docs_json")
                .apply()
        }
        loadData()
        startFirestoreSync()
    }

    private fun startFirestoreSync() {
        if (!firebase.isInitialized) return

        // 1. Real-time Members Sync from Firestore (Single Source of Truth)
        firebase.startMembersListener { firestoreMembers ->
            if (firestoreMembers.isNotEmpty()) {
                val normalized = FamilyTreeUtils.normalizeFamilyRelationships(firestoreMembers)
                _members.value = normalized
                cacheMembersLocally(normalized)

                // Sync currently logged in user's profile with Firestore record
                val cur = _currentUser.value
                if (cur != null && cur.role != "admin") {
                    val matchingMember = normalized.find {
                        it.id == cur.memberId ||
                        (it.accountUid != null && it.accountUid == cur.uid) ||
                        (it.email != null && it.email.equals(cur.email, ignoreCase = true))
                    }
                    if (matchingMember != null) {
                        val updatedUser = cur.copy(
                            isApproved = true,
                            role = "member",
                            memberId = cur.memberId ?: matchingMember.id
                        )
                        _currentUser.value = updatedUser
                        persistUser(updatedUser)
                    }
                }
            } else {
                // Initial bootstrap: seed baseline family to Firestore
                repositoryScope.launch {
                    firebase.syncAllMembers(SeedData.initialMembers)
                }
            }
        }

        // 2. Real-time Documents Sync from Firestore
        firebase.startDocumentsListener { firestoreDocs ->
            if (firestoreDocs.isNotEmpty()) {
                _documents.value = firestoreDocs
                cacheDocumentsLocally(firestoreDocs)
            } else {
                repositoryScope.launch {
                    for (doc in SeedData.initialDocuments) {
                        firebase.saveDocument(doc)
                    }
                }
            }
        }

        // 3. Real-time Settings Sync from Firestore
        firebase.startSettingsListener { firestoreSettings ->
            _settings.value = firestoreSettings
            prefs.edit()
                .putString("family_name", firestoreSettings.familyName)
                .apply()
        }

        // 4. Real-time Pending Registrations Sync from Firestore
        firebase.startRegistrationsListener { pendingList ->
            _pendingRegistrations.value = pendingList
        }
    }

    private fun cacheMembersLocally(members: List<FamilyMember>) {
        val array = JSONArray()
        for (m in members) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("fullName", m.fullName)
            obj.put("relation", m.relation)
            obj.put("generation", m.generation)
            obj.put("gender", m.gender)
            obj.put("fatherId", m.fatherId)
            obj.put("motherId", m.motherId)
            obj.put("spouseId", m.spouseId)
            val cArr = JSONArray()
            m.childrenIds.forEach { cArr.put(it) }
            obj.put("childrenIds", cArr)
            obj.put("cnic", m.cnic)
            obj.put("phone", m.phone)
            obj.put("email", m.email)
            obj.put("city", m.city)
            obj.put("dateOfBirth", m.dateOfBirth)
            obj.put("about", m.about)
            obj.put("profileImageUrl", m.profileImageUrl)
            obj.put("accountUid", m.accountUid)
            obj.put("isApproved", m.isApproved)
            array.put(obj)
        }
        prefs.edit().putString("members_json", array.toString()).apply()
    }

    private fun cacheDocumentsLocally(docs: List<FamilyDocument>) {
        val array = JSONArray()
        for (d in docs) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("memberId", d.memberId)
            obj.put("memberName", d.memberName)
            obj.put("title", d.title)
            obj.put("category", d.category)
            obj.put("fileName", d.fileName)
            obj.put("fileType", d.fileType)
            obj.put("fileSize", d.fileSize)
            obj.put("date", d.date)
            obj.put("url", d.url)
            obj.put("localUri", d.localUri)
            obj.put("uploadedBy", d.uploadedBy)
            array.put(obj)
        }
        prefs.edit().putString("docs_json", array.toString()).apply()
    }

    private fun loadData() {
        // Load Settings (Passcode is server-managed and never stored in SharedPreferences)
        val familyName = prefs.getString("family_name", "My Family") ?: "My Family"
        _settings.value = FamilySettings(
            familyPasscode = "",
            familyName = familyName
        )

        // Load Members
        val membersJson = prefs.getString("members_json", null)
        if (!membersJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(membersJson)
                val list = mutableListOf<FamilyMember>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val childrenArr = obj.optJSONArray("childrenIds")
                    val childrenList = mutableListOf<String>()
                    if (childrenArr != null) {
                        for (j in 0 until childrenArr.length()) {
                            childrenList.add(childrenArr.getString(j))
                        }
                    }
                    list.add(
                        FamilyMember(
                            id = obj.getString("id"),
                            fullName = obj.getString("fullName"),
                            relation = obj.getString("relation"),
                            generation = obj.optInt("generation", 1),
                            gender = obj.optString("gender", "Male"),
                            fatherId = if (obj.has("fatherId") && !obj.isNull("fatherId")) obj.getString("fatherId") else null,
                            motherId = if (obj.has("motherId") && !obj.isNull("motherId")) obj.getString("motherId") else null,
                            spouseId = if (obj.has("spouseId") && !obj.isNull("spouseId")) obj.getString("spouseId") else null,
                            childrenIds = childrenList,
                            cnic = if (obj.has("cnic")) obj.getString("cnic") else null,
                            phone = if (obj.has("phone")) obj.getString("phone") else null,
                            email = if (obj.has("email")) obj.getString("email") else null,
                            city = obj.optString("city", "Lahore"),
                            dateOfBirth = if (obj.has("dateOfBirth")) obj.getString("dateOfBirth") else null,
                            about = if (obj.has("about")) obj.getString("about") else null,
                            profileImageUrl = if (obj.has("profileImageUrl")) obj.getString("profileImageUrl") else null,
                            isApproved = obj.optBoolean("isApproved", true)
                        )
                    )
                }
                _members.value = FamilyTreeUtils.normalizeFamilyRelationships(list)
            } catch (e: Exception) {
                _members.value = FamilyTreeUtils.normalizeFamilyRelationships(SeedData.initialMembers)
            }
        } else {
            _members.value = FamilyTreeUtils.normalizeFamilyRelationships(SeedData.initialMembers)
            saveMembers()
        }

        // Load Documents
        val docsJson = prefs.getString("docs_json", null)
        if (!docsJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(docsJson)
                val list = mutableListOf<FamilyDocument>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        FamilyDocument(
                            id = obj.getString("id"),
                            memberId = obj.getString("memberId"),
                            memberName = obj.getString("memberName"),
                            title = obj.getString("title"),
                            category = obj.optString("category", "Other"),
                            fileName = obj.optString("fileName", "document.pdf"),
                            fileType = obj.optString("fileType", "PDF"),
                            fileSize = obj.optString("fileSize", "1.0 MB"),
                            date = obj.optString("date", "Recent"),
                            url = if (obj.has("url")) obj.getString("url") else null,
                            localUri = if (obj.has("localUri")) obj.getString("localUri") else null,
                            uploadedBy = obj.optString("uploadedBy", "Family Member")
                        )
                    )
                }
                _documents.value = list
            } catch (e: Exception) {
                _documents.value = SeedData.initialDocuments
            }
        } else {
            _documents.value = SeedData.initialDocuments
            saveDocuments()
        }

        // Check logged in user
        val loggedUid = prefs.getString("user_uid", null)
        val loggedRole = prefs.getString("user_role", null)
        val loggedEmail = prefs.getString("user_email", null)
        val loggedName = prefs.getString("user_name", null)
        val loggedPhone = prefs.getString("user_phone", "") ?: ""
        val loggedMemberId = prefs.getString("user_member_id", null)
        val loggedVerified = prefs.getBoolean("user_email_verified", false)
        val loggedApproved = prefs.getBoolean("user_is_approved", false)

        if (loggedUid != null && loggedRole != null && loggedEmail != null && loggedName != null) {
            val member = _members.value.find {
                it.id == loggedMemberId ||
                (it.email != null && it.email.equals(loggedEmail, ignoreCase = true)) ||
                (it.accountUid != null && it.accountUid == loggedUid)
            }
            val isApproved = loggedApproved || (member?.isApproved == true) || (loggedRole == "admin")
            val isVerified = if (isApproved) true else loggedVerified

            val restored = UserAccount(
                uid = loggedUid,
                email = loggedEmail,
                fullName = loggedName,
                phone = loggedPhone,
                role = if (isApproved) "member" else loggedRole,
                memberId = loggedMemberId ?: member?.id,
                isEmailVerified = isVerified,
                isApproved = isApproved
            )
            _currentUser.value = restored
            startUserSync(loggedUid)
        }
    }

    private fun saveMembers() {
        val array = JSONArray()
        for (m in _members.value) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("fullName", m.fullName)
            obj.put("relation", m.relation)
            obj.put("generation", m.generation)
            obj.put("gender", m.gender)
            obj.put("fatherId", m.fatherId)
            obj.put("motherId", m.motherId)
            obj.put("spouseId", m.spouseId)
            val cArr = JSONArray()
            m.childrenIds.forEach { cArr.put(it) }
            obj.put("childrenIds", cArr)
            obj.put("cnic", m.cnic)
            obj.put("phone", m.phone)
            obj.put("email", m.email)
            obj.put("city", m.city)
            obj.put("dateOfBirth", m.dateOfBirth)
            obj.put("about", m.about)
            obj.put("profileImageUrl", m.profileImageUrl)
            obj.put("isApproved", m.isApproved)
            array.put(obj)
        }
        prefs.edit().putString("members_json", array.toString()).apply()
        repositoryScope.launch {
            if (firebase.isInitialized && _currentUser.value?.role == "admin") {
                firebase.syncAllMembers(_members.value)
            }
        }
    }

    private fun saveDocuments() {
        val array = JSONArray()
        for (d in _documents.value) {
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("memberId", d.memberId)
            obj.put("memberName", d.memberName)
            obj.put("title", d.title)
            obj.put("category", d.category)
            obj.put("fileName", d.fileName)
            obj.put("fileType", d.fileType)
            obj.put("fileSize", d.fileSize)
            obj.put("date", d.date)
            obj.put("url", d.url)
            obj.put("localUri", d.localUri)
            obj.put("uploadedBy", d.uploadedBy)
            array.put(obj)
        }
        prefs.edit().putString("docs_json", array.toString()).apply()
        repositoryScope.launch {
            if (firebase.isInitialized) {
                for (doc in _documents.value) {
                    firebase.saveDocument(doc)
                }
            }
        }
    }

    private var _pendingRegistrationPasscode: String = ""

    fun setRegistrationPasscode(passcode: String) {
        _pendingRegistrationPasscode = passcode
    }

    fun getRegistrationPasscode(): String {
        return _pendingRegistrationPasscode
    }

    fun verifyPasscode(input: String): Boolean {
        return _settings.value.familyPasscode.isNotEmpty() && input == _settings.value.familyPasscode
    }

    fun updatePasscode(newPasscode: String) {
        _settings.value = _settings.value.copy(familyPasscode = newPasscode)
        repositoryScope.launch {
            if (firebase.isInitialized) {
                firebase.savePasscodeSecret(newPasscode)
            }
        }
    }

    suspend fun login(emailOrPhone: String, pass: String): Result<UserAccount> {
        val trimmed = emailOrPhone.trim()

        var currentMembers = _members.value
        val localMember = currentMembers.find {
            (it.email != null && it.email.equals(trimmed, ignoreCase = true)) ||
            (it.phone != null && it.phone.replace(" ", "").replace("-", "") == trimmed.replace(" ", "").replace("-", "")) ||
            it.fullName.equals(trimmed, ignoreCase = true)
        }

        val targetEmail = when {
            trimmed.contains("@") -> trimmed
            localMember?.email != null && localMember.email.contains("@") -> localMember.email
            else -> "$trimmed@qadirfamily.com"
        }

        var firebaseUser: FirebaseUser? = null
        var isEmailVerified = false

        if (firebase.isInitialized || firebase.auth != null) {
            val authResult = firebase.signInWithEmail(targetEmail, pass)
            firebaseUser = authResult.getOrNull()
            if (firebaseUser != null) {
                val checkRes = firebase.reloadUserAndCheckVerified()
                isEmailVerified = checkRes.getOrDefault(firebaseUser.isEmailVerified)
            } else {
                val ex = authResult.exceptionOrNull() ?: Exception("Login failed. Please verify your email and password.")
                return Result.failure(ex)
            }
        }

        // Single Source of Truth: Check Firestore for existing member record
        var member = localMember
        if (member == null && firebase.isInitialized) {
            val foundInFirestore = firebase.findMemberInFirestore(
                uid = firebaseUser?.uid,
                email = targetEmail,
                phone = trimmed
            ).getOrNull()
            if (foundInFirestore != null) {
                member = foundInFirestore
                val updated = (currentMembers + foundInFirestore).distinctBy { it.id }
                _members.value = FamilyTreeUtils.normalizeFamilyRelationships(updated)
                cacheMembersLocally(_members.value)
            }
        }

        val userUid = firebaseUser?.uid ?: (member?.accountUid ?: UUID.randomUUID().toString())
        var userRole = "member"
        var userApproved = false
        var userMemberId = member?.id

        if (firebaseUser != null && firebase.isInitialized) {
            val claimsRes = firebase.checkAdminClaims(forceRefresh = true)
            val hasAdminClaims = claimsRes.getOrDefault(false)

            val userDocRes = firebase.fetchUserAccount(userUid)
            val userDoc = userDocRes.getOrNull()

            if (hasAdminClaims || userDoc?.role == "admin") {
                userRole = "admin"
                userApproved = true
                isEmailVerified = true
                userMemberId = userDoc?.memberId ?: member?.id ?: _settings.value.rootFatherId.ifEmpty { null }
            } else if (userDoc != null) {
                userRole = userDoc.role
                userApproved = userDoc.isApproved
                userMemberId = userDoc.memberId ?: userMemberId
            }
        } else if (member != null && member.isApproved) {
            userApproved = true
        }

        val account = UserAccount(
            uid = userUid,
            email = firebaseUser?.email ?: member?.email ?: targetEmail,
            fullName = member?.fullName ?: firebaseUser?.displayName ?: "Family Member",
            phone = member?.phone ?: "",
            role = userRole,
            memberId = userMemberId,
            isEmailVerified = isEmailVerified,
            isApproved = userApproved
        )

        _currentUser.value = account
        persistUser(account)
        if (firebaseUser != null) {
            startUserSync(firebaseUser.uid)
        }

        return Result.success(account)
    }

    fun getRootParentsCount(): Int {
        return _members.value.count { it.generation == 0 }
    }

    fun canRegisterRootParent(): Boolean {
        return getRootParentsCount() < 2
    }

    suspend fun registerMember(
        fullName: String,
        cnic: String,
        fatherName: String,
        motherName: String,
        email: String,
        phone: String,
        password: String,
        selectedFatherId: String?,
        selectedMotherId: String?,
        isRootParent: Boolean = false,
        gender: String = "Male",
        dateOfBirth: String = "01-01-2000"
    ): Result<FamilyMember> {
        val trimmedEmail = email.trim()
        val current = _members.value

        // Check duplicate CNIC
        if (cnic.isNotEmpty()) {
            val existingWithCnic = current.find { it.cnic != null && it.cnic == cnic }
            if (existingWithCnic != null) {
                return Result.failure(Exception("A family member with CNIC $cnic is already registered!"))
            }
        }

        // Check duplicate email
        val existingWithEmail = current.find { it.email != null && it.email.equals(trimmedEmail, ignoreCase = true) }
        if (existingWithEmail != null) {
            return Result.failure(Exception("A family member with email $trimmedEmail is already registered!"))
        }

        if (isRootParent) {
            val roots = current.filter { it.generation == 0 }
            if (roots.size >= 2) {
                return Result.failure(Exception("Maximum of 2 Root Parents are allowed in the entire family tree."))
            }
            if (roots.size == 1 && roots[0].gender.equals(gender, ignoreCase = true)) {
                return Result.failure(Exception("A Root Parent with gender $gender already exists on the trunk."))
            }
        }

        // 1. Create Firebase Auth account & Send Official Verification Email
        var firebaseUid = UUID.randomUUID().toString()
        var emailVerified = false

        if (firebase.isInitialized || firebase.auth != null) {
            val signUpRes = firebase.signUpWithEmail(trimmedEmail, password)
            if (signUpRes.isSuccess) {
                val fbUser = signUpRes.getOrNull()
                if (fbUser != null) {
                    firebaseUid = fbUser.uid
                    emailVerified = fbUser.isEmailVerified
                    // Send official Firebase verification email
                    val sendRes = firebase.sendVerificationEmail()
                    if (sendRes.isFailure) {
                        Log.w("FamilyRepository", "sendVerificationEmail notice: ${sendRes.exceptionOrNull()?.message}")
                    }
                }
            } else {
                // If account exists in Firebase Auth, attempt sign in and send verification
                val signInRes = firebase.signInWithEmail(trimmedEmail, password)
                if (signInRes.isSuccess) {
                    val fbUser = signInRes.getOrNull()
                    if (fbUser != null) {
                        firebaseUid = fbUser.uid
                        emailVerified = fbUser.isEmailVerified
                        if (!emailVerified) {
                            firebase.sendVerificationEmail()
                        }
                    }
                } else {
                    val authEx = signUpRes.exceptionOrNull() ?: signInRes.exceptionOrNull() ?: Exception("Firebase authentication failed for $trimmedEmail")
                    Log.e("FamilyRepository", "Firebase auth failure: ${authEx.message}", authEx)
                    return Result.failure(authEx)
                }
            }
        }

        // Determine parent ID & generation
        val father = current.find { it.id == selectedFatherId || (fatherName.isNotBlank() && it.fullName.equals(fatherName, ignoreCase = true)) }
        val mother = current.find { it.id == selectedMotherId || (motherName.isNotBlank() && it.fullName.equals(motherName, ignoreCase = true)) }

        val (existingRootFather, existingRootMother) = FamilyTreeUtils.findRootParents(current)
        val otherRoot = if (gender.equals("Male", ignoreCase = true)) existingRootMother else existingRootFather
        val isAdminCaller = _currentUser.value?.role == "admin"

        if (isRootParent && isAdminCaller) {
            val newId = "m_" + UUID.randomUUID().toString().substring(0, 8)
            val newMember = FamilyMember(
                id = newId,
                fullName = fullName,
                relation = if (gender == "Male") "Grand Father (Trunk)" else "Grand Mother (Trunk)",
                generation = 0,
                gender = gender,
                fatherId = null,
                motherId = null,
                spouseId = otherRoot?.id,
                cnic = cnic.ifEmpty { null },
                phone = phone.ifEmpty { null },
                email = trimmedEmail,
                city = "Lahore",
                dateOfBirth = dateOfBirth.ifBlank { "01-01-2000" },
                about = "Root Parent & Founder of the Qadir Family Tree.",
                accountUid = firebaseUid,
                isApproved = true
            )

            // Save user document first
            if (firebase.isInitialized || firebase.firestore != null) {
                val userDocRes = firebase.saveUserDocument(
                    uid = firebaseUid,
                    email = trimmedEmail,
                    fullName = fullName,
                    phone = phone,
                    role = "admin",
                    memberId = newId,
                    isApproved = true
                )
                if (userDocRes.isFailure) {
                    val ex = userDocRes.exceptionOrNull() ?: Exception("Firestore user document creation failed.")
                    Log.e("FamilyRepository", "Firestore saveUserDocument error: ${ex.message}", ex)
                    return Result.failure(ex)
                }

                // Atomic transaction: claims root slot and creates member record in ONE write operation
                val txRes = firebase.registerMemberTransactional(newMember, isRootParent = true)
                if (txRes.isFailure) {
                    val ex = txRes.exceptionOrNull() ?: Exception("Root Parent transaction failed.")
                    Log.e("FamilyRepository", "Firestore registration transaction error: ${ex.message}", ex)
                    return Result.failure(ex)
                }
            }

            val updatedList = current.map { m ->
                if (otherRoot != null && m.id == otherRoot.id) {
                    m.copy(spouseId = newId)
                } else {
                    m
                }
            } + newMember

            _members.value = FamilyTreeUtils.normalizeFamilyRelationships(updatedList)
            saveMembers()

            val account = UserAccount(
                uid = firebaseUid,
                email = trimmedEmail,
                fullName = fullName,
                phone = phone,
                role = "admin",
                memberId = newId,
                isEmailVerified = emailVerified,
                isApproved = true
            )
            _currentUser.value = account
            persistUser(account)
            startUserSync(firebaseUid)

            return Result.success(newMember)
        } else {
            // NORMAL MEMBER REGISTRATION (OR ROOT PARENT APPLICANT):
            // Normal users must NOT directly create a document in /members.
            // Writes to /registrations/{registrationId} with status = "pending".
            // Forces memberId = "" and isApproved = false in users/{uid}.
            val regId = "reg_$firebaseUid"
            val pendingReg = PendingRegistration(
                id = regId,
                uid = firebaseUid,
                fullName = fullName,
                email = trimmedEmail,
                phone = phone,
                gender = gender,
                dateOfBirth = dateOfBirth.ifBlank { "01-01-2000" },
                fatherName = fatherName,
                selectedFatherId = selectedFatherId,
                motherName = motherName,
                selectedMotherId = selectedMotherId,
                passcode = getRegistrationPasscode(),
                cnic = cnic.ifEmpty { null },
                isRootParent = isRootParent,
                status = "pending",
                createdAt = System.currentTimeMillis()
            )

            if (firebase.isInitialized || firebase.firestore != null) {
                val userDocRes = firebase.saveUserDocument(
                    uid = firebaseUid,
                    email = trimmedEmail,
                    fullName = fullName,
                    phone = phone,
                    role = "member",
                    memberId = "",
                    isApproved = false
                )
                if (userDocRes.isFailure) {
                    val ex = userDocRes.exceptionOrNull() ?: Exception("Firestore user document creation failed.")
                    Log.e("FamilyRepository", "Firestore saveUserDocument error: ${ex.message}", ex)
                    return Result.failure(ex)
                }

                val regRes = firebase.saveRegistration(pendingReg)
                if (regRes.isFailure) {
                    val ex = regRes.exceptionOrNull() ?: Exception("Failed to submit pending registration.")
                    Log.e("FamilyRepository", "saveRegistration error: ${ex.message}", ex)
                    return Result.failure(ex)
                }
            }

            val account = UserAccount(
                uid = firebaseUid,
                email = trimmedEmail,
                fullName = fullName,
                phone = phone,
                role = "member",
                memberId = null,
                isEmailVerified = emailVerified,
                isApproved = false
            )
            _currentUser.value = account
            persistUser(account)
            startUserSync(firebaseUid)

            val placeholderMember = FamilyMember(
                id = "pending_$firebaseUid",
                fullName = fullName,
                relation = "Pending Verification",
                generation = 1,
                gender = gender,
                email = trimmedEmail,
                phone = phone,
                accountUid = firebaseUid,
                isApproved = false
            )
            return Result.success(placeholderMember)
        }
    }

    suspend fun checkEmailVerification(): Result<Boolean> {
        if (!firebase.isInitialized) {
            val user = _currentUser.value?.copy(isEmailVerified = true)
            if (user != null) {
                _currentUser.value = user
                persistUser(user)
            }
            return Result.success(true)
        }

        val reloadResult = firebase.reloadUserAndCheckVerified()
        return reloadResult.map { isVerified ->
            if (isVerified) {
                val user = _currentUser.value?.copy(isEmailVerified = true)
                if (user != null) {
                    _currentUser.value = user
                    persistUser(user)
                }
            }
            isVerified
        }
    }

    suspend fun resendVerificationEmail(): Result<Unit> {
        if (!firebase.isInitialized) {
            return Result.success(Unit)
        }
        return firebase.sendVerificationEmail()
    }

    fun isCurrentUserEmailVerified(): Boolean {
        if (!firebase.isInitialized) return true
        val cur = _currentUser.value
        if (cur?.role == "admin") return true
        return firebase.isCurrentUserEmailVerified() || (cur?.isEmailVerified == true)
    }

    /**
     * Resolves the FamilyMember profile corresponding to the currently authenticated user.
     * Matches via memberId, accountUid, or email address.
     */
    fun getLoggedInMember(): FamilyMember? {
        val cur = _currentUser.value ?: return null
        if (cur.role == "admin") return null
        val currentMembers = _members.value
        return currentMembers.find {
            (cur.memberId != null && it.id == cur.memberId) ||
            (!it.accountUid.isNullOrBlank() && it.accountUid == cur.uid) ||
            (!it.email.isNullOrBlank() && it.email.equals(cur.email, ignoreCase = true))
        }
    }

    /**
     * Determines whether the current user is authorized to add a child under a specific member's branch.
     * - Admin can add children to any member branch.
     * - Normal members can ONLY add children under their own branch (or spouse's branch).
     */
    fun canUserAddChildTo(targetMemberId: String): Boolean {
        val cur = _currentUser.value ?: return false
        if (cur.role == "admin") return true
        val loggedInMember = getLoggedInMember() ?: return false
        if (targetMemberId == loggedInMember.id) return true
        val spouse = FamilyTreeUtils.findSpouseOf(loggedInMember, _members.value)
        return spouse != null && targetMemberId == spouse.id
    }

    /**
     * Adds a child to the family tree with server-grade branch validation.
     * Strictly disallows non-admin users from injecting children into other members' branches.
     */
    fun addChild(
        fullName: String,
        relation: String,
        dateOfBirth: String?,
        cnic: String?,
        parentBranchId: String,
        gender: String = "Male",
        profileImageUrl: String? = null
    ): Result<FamilyMember> {
        val curUser = _currentUser.value
        val current = _members.value

        // Security Enforcement: Branch ownership validation
        val effectiveParentId: String
        if (curUser?.role == "admin") {
            effectiveParentId = parentBranchId
        } else {
            val loggedInMember = getLoggedInMember()
                ?: return Result.failure(Exception("Unauthorized: You must be an approved family member to add someone."))
            val spouse = FamilyTreeUtils.findSpouseOf(loggedInMember, current)
            
            // Client parentId is validated against own branch & spouse
            if (parentBranchId == loggedInMember.id || (spouse != null && parentBranchId == spouse.id)) {
                effectiveParentId = parentBranchId
            } else {
                // Never trust unverified client parentId - lock to own branch
                effectiveParentId = loggedInMember.id
            }
        }

        val parent = current.find { it.id == effectiveParentId }
            ?: return Result.failure(Exception("Parent branch member not found."))

        val (rootFather, rootMother) = FamilyTreeUtils.findRootParents(current)
        val isFirstGen = parent.generation == 0

        val newId = "m_" + UUID.randomUUID().toString().substring(0, 8)

        val (fId, mId, generation) = if (isFirstGen) {
            Triple(rootFather?.id, rootMother?.id, 1)
        } else {
            val spouse = FamilyTreeUtils.findSpouseOf(parent, current)
            val father = if (parent.gender.equals("Male", ignoreCase = true)) parent else spouse?.takeIf { it.gender.equals("Male", ignoreCase = true) }
            val mother = if (parent.gender.equals("Female", ignoreCase = true)) parent else spouse?.takeIf { it.gender.equals("Female", ignoreCase = true) }
            Triple(father?.id, mother?.id, parent.generation + 1)
        }

        val newMember = FamilyMember(
            id = newId,
            fullName = fullName,
            relation = relation.ifEmpty { if (generation == 1) "Son / Daughter" else "Child" },
            generation = generation,
            gender = gender,
            fatherId = fId,
            motherId = mId,
            spouseId = null,
            childrenIds = emptyList(),
            cnic = cnic,
            phone = null,
            email = null,
            city = parent.city,
            dateOfBirth = dateOfBirth ?: "01-01-2005",
            about = "Child in the Qadir Family lineage.",
            profileImageUrl = profileImageUrl,
            isApproved = true // Children added by approved members/admin are active members
        )

        val parentsToUpdate = setOfNotNull(fId, mId, effectiveParentId)
        val updatedList = current.map { m ->
            if (parentsToUpdate.contains(m.id)) {
                m.copy(childrenIds = (m.childrenIds + newId).distinct())
            } else {
                m
            }
        } + newMember

        val normalized = FamilyTreeUtils.normalizeFamilyRelationships(updatedList)
        _members.value = normalized
        saveMembers()
        repositoryScope.launch {
            if (firebase.isInitialized || firebase.firestore != null) {
                firebase.saveMember(newMember)
                if (!cnic.isNullOrBlank()) {
                    firebase.saveSensitiveRecord(newMember.id, null, cnic)
                }
            }
        }
        return Result.success(newMember)
    }

    fun updateMember(updated: FamilyMember) {
        val current = _members.value
        _members.value = current.map { if (it.id == updated.id) updated else it }
        saveMembers()
    }

    fun deleteMember(memberId: String) {
        val current = _members.value
        // Remove as child from parents and delete
        val updated = current
            .filter { it.id != memberId }
            .map { m ->
                if (m.childrenIds.contains(memberId)) {
                    m.copy(childrenIds = m.childrenIds.filter { it != memberId })
                } else {
                    m
                }
            }
        _members.value = updated
        saveMembers()

        // Also remove their documents
        _documents.value = _documents.value.filter { it.memberId != memberId }
        saveDocuments()
    }

    fun moveMemberBranch(memberId: String, newParentId: String) {
        val current = _members.value
        val newParent = current.find { it.id == newParentId } ?: return
        val target = current.find { it.id == memberId } ?: return

        val oldFatherId = target.fatherId
        val oldMotherId = target.motherId

        val updated = current.map { m ->
            when (m.id) {
                oldFatherId, oldMotherId -> {
                    m.copy(childrenIds = m.childrenIds.filter { it != memberId })
                }
                newParentId -> {
                    m.copy(childrenIds = if (m.childrenIds.contains(memberId)) m.childrenIds else m.childrenIds + memberId)
                }
                memberId -> {
                    if (newParent.gender == "Female") {
                        m.copy(motherId = newParentId, generation = newParent.generation + 1)
                    } else {
                        m.copy(fatherId = newParentId, generation = newParent.generation + 1)
                    }
                }
                else -> m
            }
        }
        _members.value = updated
        saveMembers()
    }

    fun addDocument(
        memberId: String,
        title: String,
        category: String,
        fileName: String,
        fileType: String = "PDF",
        fileSize: String = "1.4 MB",
        fileUri: String? = null,
        cloudUrl: String? = null
    ): FamilyDocument {
        val member = _members.value.find { it.id == memberId }
        val docId = "doc_" + UUID.randomUUID().toString().substring(0, 8)
        val newDoc = FamilyDocument(
            id = docId,
            memberId = memberId,
            memberName = member?.fullName ?: "Qadir Member",
            title = title,
            category = category,
            fileName = fileName,
            fileType = fileType,
            fileSize = fileSize,
            date = "Recent",
            url = cloudUrl,
            localUri = fileUri,
            uploadedBy = _currentUser.value?.fullName ?: "Family Admin"
        )
        _documents.value = listOf(newDoc) + _documents.value
        saveDocuments()

        // Asynchronously upload to Cloudinary if cloudUrl is not already present
        if (cloudUrl == null && !fileUri.isNullOrEmpty()) {
            repositoryScope.launch {
                try {
                    val uri = android.net.Uri.parse(fileUri)
                    val res = cloudinary.uploadFromUri(uri, fileName)
                    val secureUrl = res.getOrNull()?.secureUrl
                    if (!secureUrl.isNullOrEmpty()) {
                        updateDocumentUrl(docId, secureUrl)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FamilyRepository", "Background Cloudinary upload failed: ${e.message}")
                }
            }
        }
        return newDoc
    }

    suspend fun uploadAndAddDocument(
        memberId: String,
        title: String,
        category: String,
        fileName: String,
        fileType: String = "PDF",
        fileSize: String = "1.4 MB",
        fileUri: String? = null
    ): Result<FamilyDocument> {
        var remoteUrl: String? = null
        if (!fileUri.isNullOrEmpty()) {
            try {
                val uri = android.net.Uri.parse(fileUri)
                val res = cloudinary.uploadFromUri(uri, fileName)
                if (res.isSuccess) {
                    remoteUrl = res.getOrNull()?.secureUrl
                }
            } catch (e: Exception) {
                android.util.Log.w("FamilyRepository", "Cloudinary upload exception: ${e.message}")
            }
        }
        val doc = addDocument(
            memberId = memberId,
            title = title,
            category = category,
            fileName = fileName,
            fileType = fileType,
            fileSize = fileSize,
            fileUri = fileUri,
            cloudUrl = remoteUrl
        )
        return Result.success(doc)
    }

    fun updateDocumentUrl(docId: String, newUrl: String) {
        _documents.value = _documents.value.map {
            if (it.id == docId) it.copy(url = newUrl) else it
        }
        saveDocuments()
    }

    fun updateRootParents(fatherName: String, motherName: String) {
        val current = _members.value
        val updated = current.map { m ->
            when (m.id) {
                _settings.value.rootFatherId -> m.copy(fullName = fatherName)
                _settings.value.rootMotherId -> m.copy(fullName = motherName)
                else -> m
            }
        }
        _members.value = updated
        saveMembers()
    }

    fun resetToCleanTree() {
        _members.value = SeedData.initialMembers
        saveMembers()
        _documents.value = emptyList()
        saveDocuments()
    }

    fun exportBackupJson(): String {
        return prefs.getString("members_json", "[]") ?: "[]"
    }

    fun deleteDocument(docId: String) {
        _documents.value = _documents.value.filter { it.id != docId }
        saveDocuments()
    }

    fun logout() {
        _currentUser.value = null
        prefs.edit()
            .remove("user_uid")
            .remove("user_role")
            .remove("user_email")
            .remove("user_name")
            .remove("user_phone")
            .remove("user_member_id")
            .remove("user_email_verified")
            .remove("user_is_approved")
            .apply()
    }

    suspend fun requestPasswordReset(email: String, cnic: String): Result<Unit> {
        return firebase.verifyAndResetPassword(email, cnic)
    }

    fun approveMember(memberId: String) {
        val current = _members.value
        val target = current.find { it.id == memberId }
        val updatedList = current.map {
            if (it.id == memberId) it.copy(isApproved = true) else it
        }
        _members.value = updatedList
        saveMembers()

        // If currently active user matches this member, immediately update and persist session
        val cur = _currentUser.value
        if (cur != null && (cur.memberId == memberId || (target?.email != null && target.email.equals(cur.email, ignoreCase = true)) || (target?.accountUid != null && target.accountUid == cur.uid))) {
            val updatedUser = cur.copy(
                isApproved = true,
                isEmailVerified = true,
                role = "member"
            )
            _currentUser.value = updatedUser
            persistUser(updatedUser)
        }

        repositoryScope.launch {
            if (firebase.isInitialized) {
                val approved = _members.value.find { it.id == memberId }
                if (approved != null) {
                    firebase.saveMember(approved)
                    val targetUid = approved.accountUid ?: (if (cur?.memberId == memberId) cur.uid else null)
                    if (targetUid != null) {
                        firebase.saveUserRole(targetUid, "member")
                    }
                }
            }
        }
    }

    suspend fun approveRegistration(registration: PendingRegistration): Result<FamilyMember> {
        val current = _members.value
        val (rootFather, rootMother) = FamilyTreeUtils.findRootParents(current)

        if (registration.isRootParent) {
            val isMale = registration.gender.equals("Male", ignoreCase = true)
            val otherRoot = if (isMale) rootMother else rootFather
            val newId = "m_" + UUID.randomUUID().toString().substring(0, 8)
            val newMember = FamilyMember(
                id = newId,
                fullName = registration.fullName,
                relation = if (isMale) "Grand Father (Trunk)" else "Grand Mother (Trunk)",
                generation = 0,
                gender = registration.gender,
                fatherId = null,
                motherId = null,
                spouseId = otherRoot?.id,
                childrenIds = emptyList(),
                cnic = null,
                phone = registration.phone.ifEmpty { null },
                email = registration.email.ifEmpty { null },
                city = "Lahore",
                dateOfBirth = registration.dateOfBirth,
                about = "Root Parent & Founder of the Qadir Family Tree.",
                accountUid = registration.uid,
                isApproved = true
            )

            if (firebase.isInitialized || firebase.firestore != null) {
                val res = firebase.registerMemberTransactional(newMember, isRootParent = true, registrationId = registration.id)
                if (res.isFailure) {
                    return Result.failure(res.exceptionOrNull() ?: Exception("Failed to approve Root Parent registration in Firestore"))
                }
            }

            val updatedList = current.map { m ->
                if (otherRoot != null && m.id == otherRoot.id) {
                    m.copy(spouseId = newId)
                } else {
                    m
                }
            } + newMember

            val normalized = FamilyTreeUtils.normalizeFamilyRelationships(updatedList)
            _members.value = normalized
            saveMembers()

            _pendingRegistrations.value = _pendingRegistrations.value.filter { it.id != registration.id }

            return Result.success(newMember)
        }

        val father = current.find { it.id == registration.selectedFatherId || (registration.fatherName.isNotBlank() && it.fullName.equals(registration.fatherName, ignoreCase = true)) }
        val mother = current.find { it.id == registration.selectedMotherId || (registration.motherName.isNotBlank() && it.fullName.equals(registration.motherName, ignoreCase = true)) }

        val isFirstGen = (father != null && father.generation == 0) ||
                         (mother != null && mother.generation == 0) ||
                         (registration.fatherName.isNotBlank() && rootFather != null && rootFather.fullName.equals(registration.fatherName, ignoreCase = true)) ||
                         (registration.motherName.isNotBlank() && rootMother != null && rootMother.fullName.equals(registration.motherName, ignoreCase = true)) ||
                         (father == null && mother == null && current.any { it.generation == 0 })

        val (fId, mId, generation) = if (isFirstGen) {
            Triple(rootFather?.id ?: father?.id ?: registration.selectedFatherId, rootMother?.id ?: mother?.id ?: registration.selectedMotherId, 1)
        } else {
            val fGen = father?.generation ?: 0
            val mGen = mother?.generation ?: 0
            val gen = maxOf(fGen, mGen) + 1
            val spouseOfFather = father?.let { FamilyTreeUtils.findSpouseOf(it, current) }
            val spouseOfMother = mother?.let { FamilyTreeUtils.findSpouseOf(it, current) }
            val resolvedFatherId = father?.id ?: spouseOfMother?.takeIf { it.gender.equals("Male", ignoreCase = true) }?.id ?: registration.selectedFatherId
            val resolvedMotherId = mother?.id ?: spouseOfFather?.takeIf { it.gender.equals("Female", ignoreCase = true) }?.id ?: registration.selectedMotherId
            Triple(resolvedFatherId, resolvedMotherId, gen)
        }

        val newId = "m_" + UUID.randomUUID().toString().substring(0, 8)
        val newMember = FamilyMember(
            id = newId,
            fullName = registration.fullName,
            relation = if (generation == 1) "Son / Daughter" else "Grandchild",
            generation = generation,
            gender = registration.gender,
            fatherId = fId,
            motherId = mId,
            spouseId = null,
            childrenIds = emptyList(),
            cnic = null, // Protected: stored in sensitive_records collection
            phone = registration.phone.ifEmpty { null },
            email = registration.email.ifEmpty { null },
            city = "Lahore",
            dateOfBirth = registration.dateOfBirth,
            about = "Approved member of the Qadir Family.",
            accountUid = registration.uid,
            isApproved = true
        )

        if (firebase.isInitialized || firebase.firestore != null) {
            val res = firebase.approveRegistrationTransactional(registration, newMember)
            if (res.isFailure) {
                return Result.failure(res.exceptionOrNull() ?: Exception("Failed to approve registration in Firestore"))
            }
        }

        val parentsToUpdate = setOfNotNull(fId, mId)
        val updatedList = current.map { m ->
            if (parentsToUpdate.contains(m.id)) {
                m.copy(childrenIds = (m.childrenIds + newId).distinct())
            } else {
                m
            }
        } + newMember

        val normalized = FamilyTreeUtils.normalizeFamilyRelationships(updatedList)
        _members.value = normalized
        saveMembers()

        _pendingRegistrations.value = _pendingRegistrations.value.filter { it.id != registration.id }

        return Result.success(newMember)
    }

    fun isCurrentUserApproved(): Boolean {
        val cur = _currentUser.value ?: return false
        if (cur.role == "admin") return true
        return cur.isApproved && !cur.memberId.isNullOrBlank()
    }

    fun canAccessFamilyTree(): Boolean {
        val cur = _currentUser.value ?: return false
        if (cur.role == "admin") return true
        return isCurrentUserEmailVerified() && cur.isApproved && !cur.memberId.isNullOrBlank()
    }

    private fun persistUser(user: UserAccount) {
        prefs.edit()
            .putString("user_uid", user.uid)
            .putString("user_role", user.role)
            .putString("user_email", user.email)
            .putString("user_name", user.fullName)
            .putString("user_phone", user.phone)
            .putString("user_member_id", user.memberId)
            .putBoolean("user_email_verified", user.isEmailVerified)
            .putBoolean("user_is_approved", user.isApproved)
            .apply()
    }

    companion object {
        @Volatile
        private var instance: FamilyRepository? = null

        fun getInstance(context: Context): FamilyRepository {
            return instance ?: synchronized(this) {
                instance ?: FamilyRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
