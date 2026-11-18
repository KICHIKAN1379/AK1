package com.kichikan.ak1.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class RepositoryCollectionTest {
    @Test
    fun querySurfaceDoesNotExposeBackingMutableList() {
        val backing = mutableListOf("اول", "دوم")
        val collection = RepositoryCollection(backing)

        assertFalse(collection is MutableList<*>)
        val detached = collection.snapshot()
        @Suppress("UNCHECKED_CAST")
        (detached as MutableList<String>).add("بیرونی")

        assertEquals(listOf("اول", "دوم"), collection.toList())
        assertEquals(listOf("اول", "دوم"), backing)
    }

    @Test
    fun commandCommitsInOrderAfterSuccessfulCompletion() {
        val collection = RepositoryCollection(mutableListOf("اول", "دوم"))

        collection.update {
            add(1, "میانی")
            this[0] = "شروع"
            removeAt(lastIndex)
        }

        assertEquals(listOf("شروع", "میانی"), collection.toList())
    }

    @Test
    fun failedCommandDoesNotPartiallyCommit() {
        val collection = RepositoryCollection(mutableListOf("پایدار"))

        assertThrows(IllegalStateException::class.java) {
            collection.update {
                add("موقت")
                error("command failed")
            }
        }

        assertEquals(listOf("پایدار"), collection.toList())
    }
}
