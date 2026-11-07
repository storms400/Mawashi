package dz.mawashi.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectIdentityTest {
    @Test
    fun applicationIdentityIsIndependent() {
        assertEquals("dz.mawashi.app", "dz.mawashi.app")
    }
}
