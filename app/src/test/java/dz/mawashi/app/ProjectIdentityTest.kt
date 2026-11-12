package dz.mawashi.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectIdentityTest {
    @Test fun debugApplicationIdentityIsIndependentV32() {
        assertEquals("dz.mawashi.app.independent.v32.debug", BuildConfig.APPLICATION_ID)
    }
}
