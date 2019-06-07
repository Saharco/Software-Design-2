import il.ac.technion.cs.softwaredesign.mocks.SecureStorageMock
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

var charset = Charsets.UTF_8

class AVLTreeTest {

    @Test
    internal fun `inserting one element and reading it`() {
        val storage = SecureStorageMock()
        val tree = AVLTree(storage)
        tree.insert("1000000/2019".toByteArray(charset), "".toByteArray(charset))
        assertTrue("".toByteArray(charset).contentEquals(tree.search("1000000/2019".toByteArray(charset))!!))
    }

    @Test
    internal fun `inserting multiple elements and reading them`() {
        val storage = SecureStorageMock()
        val tree = AVLTree(storage)
        for (i in 0..100) {
            tree.insert(("100000$i/2019").toByteArray(charset), "$i".toByteArray(charset))
        }
        for (i in 0..100) {
            assertTrue("$i".toByteArray(charset).contentEquals(tree.search(("100000$i/2019").toByteArray(charset))!!))
        }
    }

    @Test
    internal fun `inserting one element and deleting it`() {
        val storage = SecureStorageMock()
        val tree = AVLTree(storage)
        tree.insert("1000000/2019".toByteArray(charset), "".toByteArray(charset))
        tree.delete("1000000/2019".toByteArray(charset))
    }

    @Test
    internal fun `inserting multiple elements and deleting them`() {
        val storage = SecureStorageMock()
        val tree = AVLTree(storage)
        val list: MutableList<Int> = (0..200).toMutableList()
        list.shuffle()
        for (i in list) {
            tree.insert(("$i/2019").toByteArray(charset), "$i".toByteArray(charset))
        }
        for (i in list.shuffled()) {
            assertTrue("$i".toByteArray(charset).contentEquals(tree.search("$i/2019".toByteArray(charset))!!))
            tree.delete("$i/2019".toByteArray(charset))
            assertEquals(null, tree.search("$i/2019".toByteArray(charset)))
        }
    }

    @Test
    internal fun `inserting and deleting one by one`() {
        val storage = SecureStorageMock()
        val tree = AVLTree(storage)
        val list: MutableList<Int> = (0..10000).toMutableList()
        list.shuffle()
        for (i in list) {
            tree.insert(("$i/2019").toByteArray(charset), "$i".toByteArray(charset))
            assertTrue("$i".toByteArray(charset).contentEquals(tree.search("$i/2019".toByteArray(charset))!!))
            tree.delete("$i/2019".toByteArray(charset))
            assertEquals(null, tree.search("$i/2019".toByteArray(charset)))
        }
    }

    @Test
    internal fun `top k test`() {
        val storage = SecureStorageMock()
        val tree = AVLTree(storage)
        for (i in 0..9) {
            tree.insert(("$i/2019").toByteArray(charset), "$i".toByteArray(charset))
        }
        for (i in 1..10) {
            for (j in 9 downTo 10 - i) {
                assertTrue(tree.topKTree(i).contains("$j"))
            }
            for (j in 10 - i - 1 downTo 1) {
                assertFalse(tree.topKTree(i).contains("$j"))
            }
        }

    }
}