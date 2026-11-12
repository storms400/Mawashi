package dz.mawashi.app
import org.junit.Assert.assertEquals
import org.junit.Test
class HerdAnimalCostTest {
 @Test fun usesSpecificAnimalAcquisitionCost(){val a=HerdAnimal("id","A-1","نعجة",12,"جيدة","",acquisitionCost=12500.0);assertEquals(12500.0,herdAnimalCostForSale(a,9000.0),0.001)}
 @Test fun usesLegacyTypeAverageForOldRecords(){val a=HerdAnimal("id","A-2","نعجة",12,"جيدة","");assertEquals(9000.0,herdAnimalCostForSale(a,9000.0),0.001)}
 @Test fun invalidFallbackCannotCreateNegativeOrNonFiniteCost(){val a=HerdAnimal("id","A-3","نعجة",12,"جيدة","");assertEquals(0.0,herdAnimalCostForSale(a,Double.NaN),0.001)}
}