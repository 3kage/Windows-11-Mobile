package com.w11mobile.core.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QemuProcessSessionTest {
    @Test
    fun resolvedExitCodeOrNull_returnsNullWhileProcessAlive() {
        QemuProcessSession.reset()
        QemuProcessSession.markLaunchStarting()
        val process = ProcessBuilder("sleep", "30").start()
        QemuProcessSession.attach(process)

        assertTrue(QemuProcessSession.isAlive())
        assertNull(QemuProcessSession.resolvedExitCodeOrNull())

        process.destroyForcibly()
        process.waitFor()
        QemuProcessSession.complete(process.exitValue())

        assertFalse(QemuProcessSession.isAlive())
        assertTrue(QemuProcessSession.resolvedExitCodeOrNull() != null)
    }
}
