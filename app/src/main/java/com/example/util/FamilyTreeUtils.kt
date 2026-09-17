package com.example.util

import com.example.model.FamilyMember
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FamilyTreeUtils {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
        SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()),
        SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()),
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()),
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()),
        SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()),
        SimpleDateFormat("d MMMM yyyy", Locale.getDefault()),
        SimpleDateFormat("yyyy", Locale.getDefault())
    )

    /**
     * Parses a Date of Birth string into epoch milliseconds for accurate age sorting.
     * Earliest birthdate = smallest timestamp (i.e. oldest person).
     * If unparseable or blank, returns Long.MAX_VALUE so they appear after known birthdates.
     */
    fun parseDateOfBirthToMillis(dob: String?): Long {
        if (dob.isNullOrBlank()) return Long.MAX_VALUE
        val trimmed = dob.trim()
        for (format in dateFormats) {
            try {
                format.isLenient = false
                val parsed = format.parse(trimmed)
                if (parsed != null) {
                    return parsed.time
                }
            } catch (_: Exception) {
                // Try next format
            }
        }
        return Long.MAX_VALUE
    }

    /**
     * Formats an input date string into a clean readable date, e.g. "15 Aug 1990".
     */
    fun formatDisplayDate(dob: String?): String {
        if (dob.isNullOrBlank()) return "Not specified"
        val trimmed = dob.trim()
        for (format in dateFormats) {
            try {
                format.isLenient = false
                val parsed = format.parse(trimmed)
                if (parsed != null) {
                    val outFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    return outFormat.format(parsed)
                }
            } catch (_: Exception) {
                // Try next
            }
        }
        return dob
    }

    /**
     * Sorts siblings strictly by age (Oldest child first).
     * Secondary deterministic sort by creation time, then ID.
     */
    fun sortSiblingsByAge(siblings: List<FamilyMember>): List<FamilyMember> {
        return siblings.sortedWith(
            compareBy<FamilyMember> { parseDateOfBirthToMillis(it.dateOfBirth) }
                .thenBy { it.createdAt }
                .thenBy { it.id }
        )
    }

    /**
     * Arranges siblings in a visually balanced layout where the OLDEST child
     * occupies the exact CENTER position, and younger siblings fan out
     * progressively around them.
     *
     * Example: [Oldest, 2nd, 3rd, 4th, 5th] -> [4th, 2nd, OLDEST, 3rd, 5th]
     */
    fun arrangeSiblingsWithOldestInCenter(sortedOldestFirst: List<FamilyMember>): List<FamilyMember> {
        if (sortedOldestFirst.size <= 1) return sortedOldestFirst
        val oldest = sortedOldestFirst.first()
        val remaining = sortedOldestFirst.drop(1)

        val left = mutableListOf<FamilyMember>()
        val right = mutableListOf<FamilyMember>()

        remaining.forEachIndexed { index, child ->
            if (index % 2 == 0) {
                left.add(0, child) // Left side
            } else {
                right.add(child)   // Right side
            }
        }

        return left + listOf(oldest) + right
    }

    /**
     * Finds root parents (Generation 0 founders: Ghulam Qadir + Safia Qadir).
     * Registration order never dictates who is father vs mother.
     */
    fun findRootParents(allMembers: List<FamilyMember>): Pair<FamilyMember?, FamilyMember?> {
        val roots = allMembers.filter { it.generation == 0 }
        if (roots.isEmpty()) return Pair(null, null)

        val father = roots.find { it.gender.equals("Male", ignoreCase = true) }
            ?: roots.find { it.fullName.contains("Ghulam", ignoreCase = true) }
            ?: if (roots.all { it.gender.equals("Female", ignoreCase = true) }) null else roots.firstOrNull()

        val mother = roots.find { it.gender.equals("Female", ignoreCase = true) && it.id != father?.id }
            ?: roots.find { it.fullName.contains("Safia", ignoreCase = true) && it.id != father?.id }
            ?: roots.find { it.id != father?.id }

        return Pair(father, mother)
    }

    /**
     * Finds spouse for a family member.
     * For Root Parents (generation 0), links the other Root Parent.
     * For other members, checks spouseId, mutual reference, or shared children.
     */
    fun findSpouseOf(member: FamilyMember?, allMembers: List<FamilyMember>): FamilyMember? {
        if (member == null) return null
        if (member.generation == 0) {
            return allMembers.find { it.generation == 0 && it.id != member.id }
        }
        return allMembers.find { other ->
            other.id != member.id && (
                other.id == member.spouseId ||
                member.spouseId == other.id ||
                other.spouseId == member.id ||
                (allMembers.any { child ->
                    (child.fatherId == member.id && child.motherId == other.id) ||
                    (child.fatherId == other.id && child.motherId == member.id)
                })
            )
        }
    }

    /**
     * Finds all first-generation children (children of the Root Parents couple).
     * All first generation children belong to BOTH root parents as a single couple.
     */
    fun findFirstGenChildren(
        allMembers: List<FamilyMember>,
        rootFather: FamilyMember?,
        rootMother: FamilyMember?
    ): List<FamilyMember> {
        val directChildren = allMembers.filter { m ->
            m.generation != 0 &&
            m.id != rootFather?.id &&
            m.id != rootMother?.id &&
            (
                m.generation == 1 ||
                (rootFather != null && (m.fatherId == rootFather.id || rootFather.childrenIds.contains(m.id))) ||
                (rootMother != null && (m.motherId == rootMother.id || rootMother.childrenIds.contains(m.id))) ||
                (rootFather != null && m.motherId == rootFather.id) ||
                (rootMother != null && m.fatherId == rootMother.id)
            )
        }

        return sortSiblingsByAge(directChildren.distinctBy { it.id })
    }

    /**
     * Finds all children of a parent member or parent couple.
     * For Root Parents (Ghulam Qadir & Safia Qadir), returns their combined first-generation children.
     * For Generation 1+ parent couples, also returns their combined children.
     */
    fun findChildrenOf(parent: FamilyMember?, allMembers: List<FamilyMember>): List<FamilyMember> {
        if (parent == null) return emptyList()

        // Root parents share all 1st generation children equally
        if (parent.generation == 0) {
            val (rootFather, rootMother) = findRootParents(allMembers)
            return findFirstGenChildren(allMembers, rootFather, rootMother)
        }

        val spouse = findSpouseOf(parent, allMembers)
        val directChildren = allMembers.filter { m ->
            m.id != parent.id && m.id != spouse?.id && m.generation != 0 &&
            (
                m.fatherId == parent.id ||
                m.motherId == parent.id ||
                parent.childrenIds.contains(m.id) ||
                (spouse != null && (
                    m.fatherId == spouse.id ||
                    m.motherId == spouse.id ||
                    spouse.childrenIds.contains(m.id)
                ))
            )
        }
        return sortSiblingsByAge(directChildren.distinctBy { it.id })
    }

    /**
     * Finds parents (father and mother) for any family member.
     * First generation children are always linked to both Root Parents (Ghulam Qadir & Safia Qadir).
     */
    fun findParentsOf(member: FamilyMember?, allMembers: List<FamilyMember>): Pair<FamilyMember?, FamilyMember?> {
        if (member == null || member.generation == 0) return Pair(null, null)

        val (rootFather, rootMother) = findRootParents(allMembers)
        val isFirstGen = member.generation == 1 ||
                         member.fatherId == rootFather?.id || member.fatherId == rootMother?.id ||
                         member.motherId == rootFather?.id || member.motherId == rootMother?.id ||
                         rootFather?.childrenIds?.contains(member.id) == true ||
                         rootMother?.childrenIds?.contains(member.id) == true

        if (isFirstGen) {
            return Pair(rootFather, rootMother)
        }

        val father = allMembers.find { it.id == member.fatherId }
            ?: if (member.motherId != null) {
                val m = allMembers.find { it.id == member.motherId }
                findSpouseOf(m, allMembers)?.takeIf { it.gender.equals("Male", ignoreCase = true) }
            } else null

        val mother = allMembers.find { it.id == member.motherId }
            ?: if (member.fatherId != null) {
                val f = allMembers.find { it.id == member.fatherId }
                findSpouseOf(f, allMembers)?.takeIf { it.gender.equals("Female", ignoreCase = true) }
            } else null

        return Pair(father, mother)
    }

    /**
     * Normalizes in-memory family data without altering existing Firestore records destructively.
     * Ensures root parents are linked as spouses, first-gen children have both parents linked,
     * and children counts remain accurate across all devices.
     */
    fun normalizeFamilyRelationships(members: List<FamilyMember>): List<FamilyMember> {
        if (members.isEmpty()) return members

        val (rootFather, rootMother) = findRootParents(members)
        val firstGen = findFirstGenChildren(members, rootFather, rootMother)
        val firstGenIds = firstGen.map { it.id }.toSet()

        return members.map { member ->
            when {
                // Root Father: link spouse to Root Mother, add all first-gen children
                rootFather != null && member.id == rootFather.id -> {
                    member.copy(
                        generation = 0,
                        spouseId = rootMother?.id ?: member.spouseId,
                        childrenIds = (member.childrenIds + firstGenIds).distinct()
                    )
                }
                // Root Mother: link spouse to Root Father, add all first-gen children
                rootMother != null && member.id == rootMother.id -> {
                    member.copy(
                        generation = 0,
                        spouseId = rootFather?.id ?: member.spouseId,
                        childrenIds = (member.childrenIds + firstGenIds).distinct()
                    )
                }
                // First Generation child: link both Root Father and Root Mother
                firstGenIds.contains(member.id) -> {
                    member.copy(
                        generation = 1,
                        fatherId = rootFather?.id ?: member.fatherId,
                        motherId = rootMother?.id ?: member.motherId
                    )
                }
                else -> member
            }
        }
    }
}
