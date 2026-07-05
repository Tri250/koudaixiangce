package com.rapidraw.core

import org.junit.Assert.*
import org.junit.Test

class DiContainerTest {

    @Test
    fun testRegisterAndResolve() {
        DiContainer.reset()
        DiContainer.register<TestService> { TestService() }
        val service = DiContainer.resolve<TestService>()
        assertNotNull(service)
    }

    @Test
    fun testRegisterSingleton() {
        DiContainer.reset()
        DiContainer.registerSingleton<TestService> { TestService() }
        val instance1 = DiContainer.resolve<TestService>()
        val instance2 = DiContainer.resolve<TestService>()
        assertSame(instance1, instance2)
    }

    @Test
    fun testRegisterCreatesNewInstances() {
        DiContainer.reset()
        DiContainer.register<TestService> { TestService() }
        val instance1 = DiContainer.resolve<TestService>()
        val instance2 = DiContainer.resolve<TestService>()
        assertNotSame(instance1, instance2)
    }

    @Test
    fun testResolveOrNull() {
        DiContainer.reset()
        assertNull(DiContainer.resolveOrNull<TestService>())
        DiContainer.register<TestService> { TestService() }
        assertNotNull(DiContainer.resolveOrNull<TestService>())
    }

    @Test(expected = IllegalStateException::class)
    fun testResolveUnregistered() {
        DiContainer.reset()
        DiContainer.resolve<TestService>()
    }

    @Test
    fun testReset() {
        DiContainer.reset()
        DiContainer.register<TestService> { TestService() }
        assertNotNull(DiContainer.resolveOrNull<TestService>())
        DiContainer.reset()
        assertNull(DiContainer.resolveOrNull<TestService>())
    }

    private class TestService {
        fun doSomething(): String = "done"
    }
}