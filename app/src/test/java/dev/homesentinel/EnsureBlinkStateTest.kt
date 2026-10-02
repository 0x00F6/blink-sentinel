package dev.homesentinel

import dev.homesentinel.domain.model.*
import dev.homesentinel.domain.usecase.EnsureBlinkState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class EnsureBlinkStateTest {
    @Test
    fun systemSelectionChangeDuringStatusCheckCancelsTheCommand() = runTest {
        var selected = "1"
        val b = FakeBlink().apply { onGet = { selected = "2" } }
        assertFalse(EnsureBlinkState(b) { selected }(BlinkStatus.ARMED))
        assertEquals("1", b.lastGetSystem)
        assertEquals(0, b.arms)
    }

    @Test
    fun statusAndCommandUseTheSameCapturedSystemId() = runTest {
        val b = FakeBlink()
        assertTrue(EnsureBlinkState(b) { "123" }(BlinkStatus.ARMED))
        assertEquals("123", b.lastGetSystem)
        assertEquals("123", b.lastCommandSystem)
    }

    @Test
    fun armChangesADisarmedSystem() = runTest {
        val b = FakeBlink()
        assertTrue(EnsureBlinkState(b)(BlinkStatus.ARMED))
        assertEquals(1, b.arms)
    }

    @Test
    fun disarmChangesAnArmedSystem() = runTest {
        val b = FakeBlink(BlinkStatus.ARMED)
        assertTrue(EnsureBlinkState(b)(BlinkStatus.DISARMED))
        assertEquals(1, b.disarms)
    }

    @Test
    fun alreadyArmedSkipsPost() = runTest {
        val b = FakeBlink(BlinkStatus.ARMED)
        assertFalse(EnsureBlinkState(b)(BlinkStatus.ARMED))
        assertEquals(0, b.arms)
    }

    @Test
    fun alreadyDisarmedSkipsPost() = runTest {
        val b = FakeBlink()
        assertFalse(EnsureBlinkState(b)(BlinkStatus.DISARMED))
        assertEquals(0, b.disarms)
    }

    @Test
    fun unknownStatusCannotIssuePost() = runTest {
        val b = FakeBlink(BlinkStatus.UNKNOWN)
        try {
            EnsureBlinkState(b)(BlinkStatus.ARMED)
            fail("Must reject unknown status")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.PROTOCOL, e.kind)
        }
        assertEquals(0, b.arms)
    }

    @Test
    fun networkFailureDoesNotIssuePost() = runTest {
        val b = FakeBlink().apply { failuresLeft = 1 }
        try {
            EnsureBlinkState(b)(BlinkStatus.ARMED)
            fail("Must propagate failure")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.NETWORK, e.kind)
        }
        assertEquals(0, b.arms)
    }

    @Test
    fun changedPresenceDuringStatusCheckCancelsCommand() = runTest {
        var wanted = true
        val b = FakeBlink().apply { onGet = { wanted = false } }
        assertFalse(EnsureBlinkState(b)(BlinkStatus.ARMED) { wanted })
        assertEquals(0, b.arms)
    }

    @Test
    fun blinkErrorIsPropagatedWithoutPost() = runTest {
        val b =
            FakeBlink().apply {
                failuresLeft = 1
                error = BlinkException(BlinkException.Kind.AUTH, "Expired session")
            }
        try {
            EnsureBlinkState(b)(BlinkStatus.ARMED)
            fail("Must propagate auth failure")
        } catch (e: BlinkException) {
            assertEquals(BlinkException.Kind.AUTH, e.kind)
        }
        assertEquals(0, b.arms)
    }
}
