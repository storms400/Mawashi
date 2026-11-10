package dz.mawashi.app
import org.junit.Assert.assertEquals
import org.junit.Test
class HerdTagGeneratorTest {
 @Test fun createsSequentialTags(){assertEquals(listOf("Q-0001","Q-0002","Q-0003"),generateHerdTags(emptyList(),3))}
 @Test fun skipsUsedTags(){assertEquals(listOf("Q-0002","Q-0004"),generateHerdTags(listOf("Q-0001","Q-0003"),2))}
 @Test(expected=IllegalArgumentException::class) fun rejectsInvalidCount(){generateHerdTags(emptyList(),0)}
}