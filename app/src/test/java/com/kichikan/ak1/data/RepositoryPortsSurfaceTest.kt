package com.kichikan.ak1.data

import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryPortsSurfaceTest {
    private val ports = listOf(
        MemberRepositoryPort::class.java,
        EconomyRepositoryPort::class.java,
        MissionRepositoryPort::class.java,
        SessionRepositoryPort::class.java,
        CatalogRepositoryPort::class.java,
        WheelRepositoryPort::class.java,
        AssistantRepositoryPort::class.java,
        CatalogBootstrapperPort::class.java
    )

    @Test
    fun collectionValuedPortMethodsExposeOnlyTheReadOnlyAdapter() {
        val collectionMethods = ports
            .flatMap { it.methods.toList() }
            .filter { java.util.Collection::class.java.isAssignableFrom(it.returnType) }

        assertTrue("Repository ports should expose collection queries", collectionMethods.isNotEmpty())

        val unsafeMethods = collectionMethods.filter {
            it.returnType != RepositoryCollection::class.java
        }
        assertTrue(
            "Collection properties must use RepositoryCollection: " +
                unsafeMethods.joinToString { "${it.declaringClass.simpleName}.${it.name}" },
            unsafeMethods.isEmpty()
        )
    }
}
