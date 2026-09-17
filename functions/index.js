/**
 * Firebase Cloud Functions for Qadir Family Platform
 *
 * Authoritative Backend Security Services:
 * 1. Administrator Custom Claims management (Strict Firebase Auth Claims, one-time bootstrap)
 * 2. Server-side registration passcode validation (Exclusive Secret Manager / Env secrets, NO Firestore fallback)
 * 3. Atomic Root-Parent concurrency control & slot claiming (Max 2: 1 Male, 1 Female, passcode/auth verified, no auto-admin)
 * 4. Member approval and account linking (users/{uid} -> members/{memberId}, server-derived lineage & validation)
 * 5. Branch-enforced Child/Member creation (parent relationship validation, server-derived generation, no fake defaults)
 * 6. Backend-signed Cloudinary upload authentication (Zero API Secret in Android APK, strictly scoped folder)
 * 7. Secure CNIC isolation to /sensitive_records collection
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");

if (admin.apps.length === 0) {
  admin.initializeApp();
}

const db = admin.firestore();
const auth = admin.auth();

// Collections
const COLLECTION_SETTINGS = "settings";
const COLLECTION_MEMBERS = "members";
const COLLECTION_USERS = "users";
const COLLECTION_REGISTRATIONS = "registrations";
const COLLECTION_SENSITIVE_RECORDS = "sensitive_records";
const COLLECTION_DOCUMENTS = "documents";

// Regex Helpers for Format Validation
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_REGEX = /^[+0-9\s\-()]{7,20}$/;
const DOB_REGEX = /^(\d{2}-\d{2}-\d{4}|\d{4}-\d{2}-\d{2})$/;

/**
 * F. Helper: Retrieve caller's verified authorization status
 * Relies strictly on Firebase Custom Claims for administrator privileges.
 * Does NOT trust users/{uid}.role or client-side flags for authorization.
 */
async function getCallerStatus(context) {
  if (!context.auth) {
    return {
      isAuthenticated: false,
      isAdmin: false,
      isApproved: false,
      memberId: null,
      uid: null,
      isEmailVerified: false
    };
  }

  const uid = context.auth.uid;
  const token = context.auth.token || {};
  const isEmailVerified = token.email_verified === true;
  // Authoritative admin check: ONLY from Firebase Custom Claims
  const isAdmin = token.admin === true || token.role === "admin";

  if (isAdmin) {
    return {
      isAuthenticated: true,
      isAdmin: true,
      isApproved: true,
      memberId: null,
      uid: uid,
      isEmailVerified: isEmailVerified
    };
  }

  const userDoc = await db.collection(COLLECTION_USERS).doc(uid).get();
  if (!userDoc.exists) {
    return {
      isAuthenticated: true,
      isAdmin: false,
      isApproved: false,
      memberId: null,
      uid: uid,
      isEmailVerified: isEmailVerified
    };
  }

  const userData = userDoc.data();
  const isApproved =
    userData.isApproved === true &&
    isEmailVerified &&
    typeof userData.memberId === "string" &&
    userData.memberId.length > 0;

  return {
    isAuthenticated: true,
    isAdmin: false,
    isApproved: isApproved,
    memberId: userData.memberId || null,
    uid: uid,
    isEmailVerified: isEmailVerified
  };
}

/**
 * Helper: Retrieve server-side registration passcode from Secret Manager / environment ONLY.
 * Stored exclusively in Secret Manager / process.env.
 * Zero Firestore fallback storage.
 */
function getServerPasscode() {
  return (process.env.FAMILY_PASSCODE || "").trim();
}

/**
 * B. Complete setAdminClaim()
 * Designates an administrator account by writing Custom Claims (`admin: true`, `role: 'admin'`).
 * Authorization:
 * - Existing administrator with verified Firebase Custom Claims (`token.admin === true`), OR
 * - One-time initial bootstrap using `process.env.BOOTSTRAP_ADMIN_KEY`.
 * Bootstrap Reservation & Failure Resilience:
 * - Atomically reserves bootstrap_state in a Firestore transaction BEFORE setting claims.
 * - Prevents concurrent requests from creating multiple initial administrators.
 * - Never calls auth.setCustomUserClaims() inside a Firestore transaction.
 * - If setting custom claims fails, removes the bootstrap reservation so the key can be used again.
 * - After a successful Custom Claim, permanently marks bootstrap_state.initialized = true.
 */
exports.setAdminClaim = functions.runWith({ secrets: ["BOOTSTRAP_ADMIN_KEY"] }).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required to modify administrator claims."
    );
  }

  const callerToken = context.auth.token || {};
  const isCallerAdmin = callerToken.admin === true || callerToken.role === "admin";

  const bootstrapKey = data.bootstrapKey ? String(data.bootstrapKey).trim() : "";
  const expectedBootstrapKey = (process.env.BOOTSTRAP_ADMIN_KEY || "").trim();

  // Validate authorization
  if (!isCallerAdmin) {
    if (!expectedBootstrapKey || bootstrapKey !== expectedBootstrapKey) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Only platform administrators with verified Custom Claims can assign administrator privileges."
      );
    }
  }

  const targetEmail = data.targetEmail ? data.targetEmail.trim().toLowerCase() : "";
  const targetUid = data.targetUid ? String(data.targetUid).trim() : "";

  let targetUser;
  if (targetUid) {
    targetUser = await auth.getUser(targetUid);
  } else if (targetEmail) {
    targetUser = await auth.getUserByEmail(targetEmail);
  } else {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "targetEmail or targetUid must be provided."
    );
  }

  // Handle bootstrap flow with atomic reservation & rollback
  if (!isCallerAdmin) {
    const bootstrapRef = db.collection(COLLECTION_SETTINGS).doc("bootstrap_state");

    // 1. Atomically reserve bootstrap_state BEFORE setting claim
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(bootstrapRef);
      if (snap.exists) {
        const state = snap.data();
        if (state.initialized === true) {
          throw new functions.https.HttpsError(
            "failed-precondition",
            "One-time administrator bootstrap has already been executed. Bootstrap key is permanently disabled."
          );
        }
        // Active reservation lock within 60 seconds
        if (state.reserved === true && Date.now() - (state.reservedAt || 0) < 60000) {
          throw new functions.https.HttpsError(
            "failed-precondition",
            "Administrator bootstrap is currently in progress. Please retry in a moment."
          );
        }
      }

      tx.set(
        bootstrapRef,
        {
          reserved: true,
          reservedBy: context.auth.uid,
          reservedAt: Date.now(),
          initialized: false
        },
        { merge: true }
      );
    });

    // 2. Set Custom Claim outside transaction
    try {
      await auth.setCustomUserClaims(targetUser.uid, {
        admin: true,
        role: "admin",
        email_verified: targetUser.emailVerified
      });
    } catch (claimErr) {
      // Setting claims failed: release reservation so key can be retried
      await bootstrapRef.set(
        {
          reserved: false,
          initialized: false,
          lastError: claimErr.message || "Failed to set custom claim",
          lastErrorAt: Date.now()
        },
        { merge: true }
      );
      throw new functions.https.HttpsError(
        "internal",
        `Failed to set administrator claims: ${claimErr.message || "Unknown error"}`
      );
    }

    // 3. Custom Claim succeeded -> Permanently consume bootstrap (initialized = true) & sync Firestore
    try {
      await bootstrapRef.set(
        {
          initialized: true,
          initializedAt: Date.now(),
          initialAdminUid: targetUser.uid,
          initialAdminEmail: targetUser.email,
          reserved: false
        },
        { merge: true }
      );

      // 4. Synchronize user document
      await db.collection(COLLECTION_USERS).doc(targetUser.uid).set(
        {
          uid: targetUser.uid,
          email: targetUser.email,
          role: "admin",
          isApproved: true,
          updatedAt: Date.now()
        },
        { merge: true }
      );
    } catch (syncErr) {
      // Claims were successfully assigned, so bootstrap MUST remain permanently consumed (never reset initialized to false).
      throw new functions.https.HttpsError(
        "internal",
        `Administrator claims successfully granted, but synchronizing Firestore user state failed: ${syncErr.message || "Unknown error"}`
      );
    }
  } else {
    // Normal admin granting claim
    await auth.setCustomUserClaims(targetUser.uid, {
      admin: true,
      role: "admin",
      email_verified: targetUser.emailVerified
    });

    // Synchronize Firestore user document (for display/profile only)
    await db.collection(COLLECTION_USERS).doc(targetUser.uid).set(
      {
        uid: targetUser.uid,
        email: targetUser.email,
        role: "admin",
        isApproved: true,
        updatedAt: Date.now()
      },
      { merge: true }
    );
  }

  return {
    success: true,
    message: `Administrator Custom Claims successfully granted to ${targetUser.email} (${targetUser.uid}).`
  };
});

/**
 * C. Complete validateRegistrationPasscode()
 * Validates the submitted passcode strictly against server-side Secret Manager / environment variable.
 * Zero Firestore fallback.
 * Passcode is NEVER returned, leaked, logged, or stored on the client.
 */
exports.validateRegistrationPasscode = functions.runWith({ secrets: ["FAMILY_PASSCODE"] }).https.onCall(async (data, context) => {
  const passcode = data.passcode ? String(data.passcode).trim() : "";
  if (!passcode) {
    return { valid: false, message: "Passcode cannot be empty." };
  }

  const serverPasscode = getServerPasscode();
  if (!serverPasscode) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Family registration passcode is not configured in server Secret Manager."
    );
  }

  const isValid = passcode === serverPasscode;
  return { valid: isValid };
});

/**
 * A. Complete registerRootParent()
 * Requirements:
 * - Require Firebase Authentication.
 * - Require verified email (`token.email_verified === true`).
 * - Require approved secure founder registration flow (passcode validation against Secret Manager or existing admin).
 * - Reject duplicate registrations: A user account (UID) already linked to a member cannot claim another root or overwrite.
 * - Validate all required fields server-side (fullName, gender strictly 'Male' or 'Female', valid DOB, city, phone/email).
 * - Exactly ONE root father and ONE root mother globally.
 * - Root slots are race-safe using a Firestore transaction (`db.runTransaction`).
 * - Third root registration must always fail.
 * - Root father and root mother share one common trunk via mutual spouseId.
 * - Root parents share trunk children (never assigned only to whichever registered first).
 * - Do NOT automatically grant admin privileges to a root parent (`role: "member"`).
 * - Root Parent and Administrator are separate roles.
 */
exports.registerRootParent = functions.runWith({ secrets: ["FAMILY_PASSCODE"] }).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Must be authenticated to register as a Root Parent."
    );
  }

  const token = context.auth.token || {};
  if (token.email_verified !== true) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Email verification is strictly required before registering as a Root Parent."
    );
  }

  const isAdmin = token.admin === true || token.role === "admin";
  const passcode = data.passcode ? String(data.passcode).trim() : "";

  // Verify registration authorization (valid server passcode OR existing admin)
  if (!isAdmin) {
    const serverPasscode = getServerPasscode();
    if (!serverPasscode || passcode !== serverPasscode) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "Invalid or missing family registration passcode."
      );
    }
  }

  const uid = context.auth.uid;
  const fullName = String(data.fullName || "").trim();
  const gender = String(data.gender || "").trim();
  const cnic = data.cnic ? String(data.cnic).trim() : null;
  const phone = data.phone ? String(data.phone).trim() : "";
  const email = data.email ? String(data.email).trim() : (token.email || "");
  const city = String(data.city || "").trim();
  const dateOfBirth = String(data.dateOfBirth || "").trim();

  // Strict Field Validation
  if (fullName.length < 2) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Full name is required and must be at least 2 characters long."
    );
  }

  if (gender !== "Male" && gender !== "Female") {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Gender must be exactly 'Male' or 'Female'."
    );
  }

  if (!city || city.length < 2) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "City is required and must be specified."
    );
  }

  if (!dateOfBirth || !DOB_REGEX.test(dateOfBirth)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Date of birth is required and must be in valid format (DD-MM-YYYY or YYYY-MM-DD)."
    );
  }

  if (phone && !PHONE_REGEX.test(phone)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Phone number format is invalid."
    );
  }

  if (email && !EMAIL_REGEX.test(email)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Email address format is invalid."
    );
  }

  const isMale = gender === "Male";
  const newMemberId = "m_" + uid.substring(0, 8);

  const result = await db.runTransaction(async (tx) => {
    // 0. Duplicate UID Check: Reject if user already has a linked member or is already approved
    const callerUserRef = db.collection(COLLECTION_USERS).doc(uid);
    const callerUserSnap = await tx.get(callerUserRef);
    if (callerUserSnap.exists) {
      const existingUserData = callerUserSnap.data();
      if (existingUserData.memberId && existingUserData.memberId.length > 0) {
        throw new functions.https.HttpsError(
          "already-exists",
          `This user account is already linked to member ID '${existingUserData.memberId}'. Duplicate registration is rejected.`
        );
      }
    }

    const callerMemberRef = db.collection(COLLECTION_MEMBERS).doc(newMemberId);
    const callerMemberSnap = await tx.get(callerMemberRef);
    if (callerMemberSnap.exists) {
      throw new functions.https.HttpsError(
        "already-exists",
        "A member profile for this user account already exists. Duplicate registration is rejected."
      );
    }

    const configRef = db.collection(COLLECTION_SETTINGS).doc("family_config");
    const configSnap = await tx.get(configRef);

    let existingFatherId = "";
    let existingMotherId = "";

    if (configSnap.exists) {
      existingFatherId = configSnap.data().rootFatherId || "";
      existingMotherId = configSnap.data().rootMotherId || "";
    }

    if (existingFatherId === newMemberId || existingMotherId === newMemberId) {
      throw new functions.https.HttpsError(
        "already-exists",
        "This user account has already registered as a Root Parent."
      );
    }

    // Strict slot validation against concurrent claims
    if (isMale && existingFatherId) {
      throw new functions.https.HttpsError(
        "already-exists",
        "A Root Father (Ghulam Qadir) is already registered on the family tree trunk."
      );
    }

    if (!isMale && existingMotherId) {
      throw new functions.https.HttpsError(
        "already-exists",
        "A Root Mother (Safia Qadir) is already registered on the family tree trunk."
      );
    }

    if (existingFatherId && existingMotherId) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Maximum of 2 Root Parents are already registered. A third root parent is strictly disallowed."
      );
    }

    const otherRootId = isMale ? existingMotherId : existingFatherId;
    let existingChildrenIds = [];

    // If spouse already registered, retrieve shared trunk children to keep trunk synchronized
    if (otherRootId) {
      const otherSnap = await tx.get(db.collection(COLLECTION_MEMBERS).doc(otherRootId));
      if (otherSnap.exists) {
        existingChildrenIds = otherSnap.data().childrenIds || [];
      }
    }

    // 1. Create member document (Strictly WITHOUT CNIC)
    const memberRef = db.collection(COLLECTION_MEMBERS).doc(newMemberId);
    const memberData = {
      id: newMemberId,
      fullName: fullName,
      relation: isMale ? "Grand Father (Trunk)" : "Grand Mother (Trunk)",
      generation: 0,
      gender: gender,
      fatherId: null,
      motherId: null,
      spouseId: otherRootId || null,
      childrenIds: existingChildrenIds,
      phone: phone || null,
      email: email || null,
      city: city,
      dateOfBirth: dateOfBirth,
      about: "Root Parent & Founder of the Qadir Family Tree.",
      accountUid: uid,
      createdAt: Date.now(),
      isApproved: true
    };
    tx.set(memberRef, memberData);

    // 2. Claim Root Parent slot in family_config atomically
    const configUpdate = {
      familyName: "Qadir Family",
      tagline: "One Family · One Tree · Forever",
      rootFatherId: isMale ? newMemberId : existingFatherId,
      rootMotherId: !isMale ? newMemberId : existingMotherId
    };
    tx.set(configRef, configUpdate, { merge: true });

    // 3. Bidirectional trunk spouse link & shared children link
    if (otherRootId) {
      const otherRef = db.collection(COLLECTION_MEMBERS).doc(otherRootId);
      tx.update(otherRef, { spouseId: newMemberId });
    }

    // 4. Isolated sensitive record for CNIC
    if (cnic) {
      const sensitiveRef = db.collection(COLLECTION_SENSITIVE_RECORDS).doc(newMemberId);
      tx.set(
        sensitiveRef,
        {
          memberId: newMemberId,
          accountUid: uid,
          cnic: cnic,
          updatedAt: Date.now()
        },
        { merge: true }
      );
    }

    // 5. Update user document (Root Parents receive role: 'member', NEVER automatic admin)
    const userRef = db.collection(COLLECTION_USERS).doc(uid);
    tx.set(
      userRef,
      {
        uid: uid,
        email: email,
        fullName: fullName,
        phone: phone,
        role: "member",
        memberId: newMemberId,
        isApproved: true,
        updatedAt: Date.now()
      },
      { merge: true }
    );

    return memberData;
  });

  return { success: true, member: result };
});

/**
 * 4. APPROVE MEMBER & LINK ACCOUNT (Transactional)
 * Admin-triggered atomic transaction:
 * - Only callable by verified administrators with Firebase Custom Claims (`token.admin === true`).
 * - Validates parent existence and explicit reciprocal spouse relationship.
 * - Derives generation server-side from parent records (`maxParentGen + 1`).
 * - Validates gender and required registration fields.
 * - Creates permanent /members/{memberId} node (without CNIC).
 * - Isolates CNIC into /sensitive_records/{memberId}.
 * - Links users/{uid}.memberId and sets isApproved = true.
 * - Updates registration status to 'approved'.
 * - Updates parent nodes' childrenIds array.
 */
exports.approveMemberTransactional = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required.");
  }

  const isCallerAdmin =
    context.auth.token.admin === true || context.auth.token.role === "admin";
  if (!isCallerAdmin) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only administrators with verified Custom Claims can approve registrations."
    );
  }

  const registrationId = data.registrationId ? String(data.registrationId).trim() : "";
  if (!registrationId) {
    throw new functions.https.HttpsError("invalid-argument", "registrationId is required.");
  }

  const regRef = db.collection(COLLECTION_REGISTRATIONS).doc(registrationId);
  const regSnap = await regRef.get();

  if (!regSnap.exists) {
    throw new functions.https.HttpsError("not-found", "Registration document not found.");
  }

  const reg = regSnap.data();
  if (reg.status === "approved") {
    return { success: true, message: "Registration already approved." };
  }

  // Validate required fields from registration document
  const fullName = String(reg.fullName || "").trim();
  const gender = String(reg.gender || "").trim();
  const city = String(reg.city || "").trim();
  const dateOfBirth = String(reg.dateOfBirth || "").trim();

  if (fullName.length < 2) {
    throw new functions.https.HttpsError("invalid-argument", "Registered full name is invalid.");
  }

  if (gender !== "Male" && gender !== "Female") {
    throw new functions.https.HttpsError("invalid-argument", "Registered gender must be 'Male' or 'Female'.");
  }

  if (!city) {
    throw new functions.https.HttpsError("invalid-argument", "Registered city is required.");
  }

  if (!dateOfBirth || !DOB_REGEX.test(dateOfBirth)) {
    throw new functions.https.HttpsError("invalid-argument", "Registered date of birth is invalid.");
  }

  const memberId = "m_" + reg.uid.substring(0, 8);
  const fatherId = data.fatherId || reg.selectedFatherId || null;
  const motherId = data.motherId || reg.selectedMotherId || null;

  // Validate parent existence and derive generation server-side
  let maxParentGen = 0;
  let fatherDoc = null;
  let motherDoc = null;

  if (fatherId) {
    const fSnap = await db.collection(COLLECTION_MEMBERS).doc(fatherId).get();
    if (!fSnap.exists) {
      throw new functions.https.HttpsError("invalid-argument", `Referenced Father ID '${fatherId}' does not exist.`);
    }
    fatherDoc = fSnap.data();
    maxParentGen = Math.max(maxParentGen, fatherDoc.generation || 0);
  }

  if (motherId) {
    const mSnap = await db.collection(COLLECTION_MEMBERS).doc(motherId).get();
    if (!mSnap.exists) {
      throw new functions.https.HttpsError("invalid-argument", `Referenced Mother ID '${motherId}' does not exist.`);
    }
    motherDoc = mSnap.data();
    maxParentGen = Math.max(maxParentGen, motherDoc.generation || 0);
  }

  // Validate explicit reciprocal spouse relationship when both are specified
  if (fatherDoc && motherDoc) {
    const isReciprocalSpouse =
      fatherDoc.spouseId === motherId &&
      motherDoc.spouseId === fatherId;

    if (!isReciprocalSpouse) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "The specified father and mother do not have a verified reciprocal spouse relationship."
      );
    }
  }

  const derivedGeneration = (fatherDoc || motherDoc) ? maxParentGen + 1 : 1;
  const parentIds = [fatherId, motherId].filter(Boolean);

  await db.runTransaction(async (tx) => {
    // 1. Permanent Member node (Strictly WITHOUT CNIC)
    const memberRef = db.collection(COLLECTION_MEMBERS).doc(memberId);
    tx.set(
      memberRef,
      {
        id: memberId,
        fullName: fullName,
        relation: derivedGeneration === 1 ? "Son / Daughter" : "Grandchild",
        generation: derivedGeneration,
        gender: gender,
        fatherId: fatherId,
        motherId: motherId,
        spouseId: null,
        childrenIds: [],
        phone: reg.phone || null,
        email: reg.email || null,
        city: city,
        dateOfBirth: dateOfBirth,
        about: reg.about || "Approved member of the Qadir Family.",
        accountUid: reg.uid,
        createdAt: Date.now(),
        isApproved: true
      },
      { merge: true }
    );

    // 2. Sensitive CNIC isolation
    if (reg.cnic) {
      const sensitiveRef = db.collection(COLLECTION_SENSITIVE_RECORDS).doc(memberId);
      tx.set(
        sensitiveRef,
        {
          memberId: memberId,
          accountUid: reg.uid,
          cnic: reg.cnic,
          updatedAt: Date.now()
        },
        { merge: true }
      );
    }

    // 3. User account linking
    const userRef = db.collection(COLLECTION_USERS).doc(reg.uid);
    tx.set(
      userRef,
      {
        memberId: memberId,
        isApproved: true,
        role: "member",
        updatedAt: Date.now()
      },
      { merge: true }
    );

    // 4. Update registration status
    tx.update(regRef, {
      status: "approved",
      memberId: memberId,
      resolvedGeneration: derivedGeneration,
      updatedAt: Date.now()
    });

    // 5. Update parent childrenIds
    for (const pId of parentIds) {
      const pRef = db.collection(COLLECTION_MEMBERS).doc(pId);
      tx.update(pRef, {
        childrenIds: admin.firestore.FieldValue.arrayUnion(memberId)
      });
    }
  });

  return { success: true, memberId: memberId };
});

/**
 * D. Complete addChildMember()
 * Requirements:
 * - Server-side branch authorization (approved member or admin).
 * - Validate both fatherId and motherId existence when supplied.
 * - Validate explicit reciprocal spouse relationship when both parents are specified (father.spouseId === motherId && mother.spouseId === fatherId).
 * - Never accept same-generation alone as proof of couple.
 * - Never trust client-supplied generation; calculate generation from validated parent records.
 * - No fake defaults (no default city or DOB like "Lahore" or "01-01-2015").
 * - Validate required DOB, city, phone/email format.
 * - Keep accountUid null until claimed.
 */
exports.addChildMember = functions.https.onCall(async (data, context) => {
  const status = await getCallerStatus(context);
  if (!status.isAuthenticated || (!status.isAdmin && !status.isApproved)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only approved, email-verified family members or admins can add children."
    );
  }

  const fullName = String(data.fullName || "").trim();
  const gender = String(data.gender || "").trim();
  const fatherId = data.fatherId ? String(data.fatherId).trim() : null;
  const motherId = data.motherId ? String(data.motherId).trim() : null;
  const city = String(data.city || "").trim();
  const dateOfBirth = String(data.dateOfBirth || "").trim();
  const phone = data.phone ? String(data.phone).trim() : null;
  const email = data.email ? String(data.email).trim() : null;

  if (fullName.length < 2) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Child full name is required and must be at least 2 characters."
    );
  }

  if (gender !== "Male" && gender !== "Female") {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Child gender must be exactly 'Male' or 'Female'."
    );
  }

  if (!city || city.length < 2) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "City is required and must be specified."
    );
  }

  if (!dateOfBirth || !DOB_REGEX.test(dateOfBirth)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Date of birth is required and must follow valid format (DD-MM-YYYY or YYYY-MM-DD)."
    );
  }

  if (phone && !PHONE_REGEX.test(phone)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Phone number format is invalid."
    );
  }

  if (email && !EMAIL_REGEX.test(email)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Email address format is invalid."
    );
  }

  if (!fatherId && !motherId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "At least one parent (father or mother) must be specified."
    );
  }

  // Validate existence of both parents and retrieve parent records
  let maxParentGen = 0;
  let fatherDoc = null;
  let motherDoc = null;

  if (fatherId) {
    const fSnap = await db.collection(COLLECTION_MEMBERS).doc(fatherId).get();
    if (!fSnap.exists) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        `Father ID '${fatherId}' does not exist in family tree.`
      );
    }
    fatherDoc = fSnap.data();
    maxParentGen = Math.max(maxParentGen, fatherDoc.generation || 0);
  }

  if (motherId) {
    const mSnap = await db.collection(COLLECTION_MEMBERS).doc(motherId).get();
    if (!mSnap.exists) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        `Mother ID '${motherId}' does not exist in family tree.`
      );
    }
    motherDoc = mSnap.data();
    maxParentGen = Math.max(maxParentGen, motherDoc.generation || 0);
  }

  // Validate explicit reciprocal spouse relationship when both parents are specified
  if (fatherDoc && motherDoc) {
    const isReciprocalSpouse =
      fatherDoc.spouseId === motherId &&
      motherDoc.spouseId === fatherId;

    if (!isReciprocalSpouse) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "The specified father and mother do not have a verified reciprocal spouse relationship."
      );
    }
  }

  // Branch Lineage Enforcement for Non-Admins:
  // Caller must be fatherId, motherId, or married to the father/mother
  if (!status.isAdmin) {
    const callerMemberDoc = await db.collection(COLLECTION_MEMBERS).doc(status.memberId).get();
    if (!callerMemberDoc.exists) {
      throw new functions.https.HttpsError("not-found", "Caller member profile not found.");
    }
    const callerMember = callerMemberDoc.data();
    const callerSpouseId = callerMember.spouseId || null;

    const isCallerFather = fatherId === status.memberId || (callerSpouseId && fatherId === callerSpouseId);
    const isCallerMother = motherId === status.memberId || (callerSpouseId && motherId === callerSpouseId);

    if (!isCallerFather && !isCallerMother) {
      throw new functions.https.HttpsError(
        "permission-denied",
        "You can only add children to your own direct family branch."
      );
    }
  }

  // Server-calculated generation (client cannot forge generation)
  const childGeneration = maxParentGen + 1;
  const newChildId = "m_" + Date.now().toString(36) + "_" + Math.random().toString(36).substring(2, 6);

  const childData = {
    id: newChildId,
    fullName: fullName,
    relation: data.relation || (gender === "Male" ? "Son" : "Daughter"),
    generation: childGeneration,
    gender: gender,
    fatherId: fatherId,
    motherId: motherId,
    spouseId: null,
    childrenIds: [],
    phone: phone,
    email: email,
    city: city,
    dateOfBirth: dateOfBirth,
    about: data.about ? String(data.about).trim() : "",
    accountUid: null,
    createdAt: Date.now(),
    isApproved: true
  };

  await db.runTransaction(async (tx) => {
    tx.set(db.collection(COLLECTION_MEMBERS).doc(newChildId), childData);

    const parentIds = [fatherId, motherId].filter(Boolean);
    for (const pId of parentIds) {
      tx.update(db.collection(COLLECTION_MEMBERS).doc(pId), {
        childrenIds: admin.firestore.FieldValue.arrayUnion(newChildId)
      });
    }
  });

  return { success: true, member: childData };
});

/**
 * E. Complete generateCloudinarySignature()
 * Requirements:
 * - Keep CLOUDINARY_API_SECRET exclusively server-side.
 * - Backend-generated HMAC-SHA1 signed uploads.
 * - Zero fallback to unsigned presets.
 * - Zero exposure of API Secret to Android.
 * - Restrict upload destination strictly to "qadir_family/documents" folder.
 */
exports.generateCloudinarySignature = functions.runWith({ secrets: ["CLOUDINARY_API_SECRET"] }).https.onCall(async (data, context) => {
  const status = await getCallerStatus(context);
  if (!status.isAuthenticated || (!status.isAdmin && !status.isApproved)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only approved, email-verified family members and admins can generate upload signatures."
    );
  }

  const folder = "qadir_family/documents";
  const timestamp = Math.floor(Date.now() / 1000);

  const apiSecret = (process.env.CLOUDINARY_API_SECRET || "").trim();
  const apiKey = (process.env.CLOUDINARY_API_KEY || "744951532476378").trim();
  const cloudName = (process.env.CLOUDINARY_CLOUD_NAME || "zway580k").trim();

  if (!apiSecret) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Server Cloudinary secret is not configured in environment/Secret Manager."
    );
  }

  // Alphabetically sorted parameters as strictly required by Cloudinary HMAC-SHA1 algorithm
  const serializedParams = `folder=${folder}&timestamp=${timestamp}${apiSecret}`;
  const signature = crypto.createHash("sha1").update(serializedParams).digest("hex");

  return {
    apiKey: apiKey,
    cloudName: cloudName,
    folder: folder,
    timestamp: timestamp,
    signature: signature
  };
});

