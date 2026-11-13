package dz.mawashi.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectIdentityTest {
    @Test fun debugApplicationIdentityUsesFinalPackageId() {
        assertEquals("dz.mawashi.app.debug", BuildConfig.APPLICATION_ID)
    }
}
