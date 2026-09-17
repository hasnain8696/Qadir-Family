package com.example

import com.example.data.CloudinaryService
import com.example.model.FamilyMember
import com.example.util.FamilyTreeUtils
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCloudinaryConfiguration() {
    assertEquals("zway580k", CloudinaryService.DEFAULT_CLOUD_NAME)
    assertEquals("744951532476378", CloudinaryService.DEFAULT_API_KEY)
  }

  @Test
  fun testRootParentsCoupleAndSharedChildren() {
    // Safia registers first as root parent
    val safia = FamilyMember(
      id = "m_safia",
      fullName = "Safia Qadir",
      relation = "Grand Mother",
      generation = 0,
      gender = "Female",
      childrenIds = listOf("m_child1", "m_child2")
    )

    // Child registered pointing to Safia as mother
    val child1 = FamilyMember(
      id = "m_child1",
      fullName = "Tariq Qadir",
      relation = "Son",
      generation = 1,
      gender = "Male",
      motherId = "m_safia",
      dateOfBirth = "1970-01-01"
    )

    // Ghulam registers later as root father
    val ghulam = FamilyMember(
      id = "m_ghulam",
      fullName = "Ghulam Qadir",
      relation = "Grand Father",
      generation = 0,
      gender = "Male",
      spouseId = "m_safia"
    )

    val rawMembers = listOf(safia, child1, ghulam)
    val normalized = FamilyTreeUtils.normalizeFamilyRelationships(rawMembers)

    val (rootFather, rootMother) = FamilyTreeUtils.findRootParents(normalized)
    assertNotNull(rootFather)
    assertEquals("m_ghulam", rootFather?.id)
    assertNotNull(rootMother)
    assertEquals("m_safia", rootMother?.id)

    // First gen children belong to both root parents
    val firstGen = FamilyTreeUtils.findFirstGenChildren(normalized, rootFather, rootMother)
    assertEquals(1, firstGen.size)
    assertEquals("m_child1", firstGen[0].id)

    // Children of Ghulam should return Tariq as well
    val ghulamChildren = FamilyTreeUtils.findChildrenOf(rootFather, normalized)
    assertEquals(1, ghulamChildren.size)
    assertEquals("m_child1", ghulamChildren[0].id)

    // Children of Safia should return Tariq as well
    val safiaChildren = FamilyTreeUtils.findChildrenOf(rootMother, normalized)
    assertEquals(1, safiaChildren.size)
    assertEquals("m_child1", safiaChildren[0].id)

    // Parents of Tariq should be Ghulam and Safia
    val (f, m) = FamilyTreeUtils.findParentsOf(child1, normalized)
    assertEquals("m_ghulam", f?.id)
    assertEquals("m_safia", m?.id)
  }

  @Test
  fun testMultiBranchDescendantsAndPermissions() {
    val father = FamilyMember(id = "f1", fullName = "Root Father", relation = "Grand Father", generation = 0, gender = "Male")
    val mother = FamilyMember(id = "m1", fullName = "Root Mother", relation = "Grand Mother", generation = 0, gender = "Female")
    val son1 = FamilyMember(id = "s1", fullName = "Son 1", relation = "Son", generation = 1, gender = "Male", fatherId = "f1", motherId = "m1")
    val son2 = FamilyMember(id = "s2", fullName = "Son 2", relation = "Son", generation = 1, gender = "Male", fatherId = "f1", motherId = "m1")
    val grandChild1 = FamilyMember(id = "gc1", fullName = "Grandchild 1", relation = "Grandson", generation = 2, gender = "Male", fatherId = "s1")

    val list = listOf(father, mother, son1, son2, grandChild1)
    val normalized = FamilyTreeUtils.normalizeFamilyRelationships(list)

    val s1Children = FamilyTreeUtils.findChildrenOf(normalized.find { it.id == "s1" }, normalized)
    assertEquals(1, s1Children.size)
    assertEquals("gc1", s1Children[0].id)

    val s2Children = FamilyTreeUtils.findChildrenOf(normalized.find { it.id == "s2" }, normalized)
    assertEquals(0, s2Children.size)
  }
}
