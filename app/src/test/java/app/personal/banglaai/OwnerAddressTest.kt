package app.personal.banglaai

import org.junit.Assert.*
import org.junit.Test

class OwnerAddressTest {
    @Test fun exactWordingAndOnce() {
        assertEquals("মিস্টার ফাতেহি চৌধুরী নাইম স্যার", OwnerAddress.OWNER_ADDRESS_BN)
        val a = OwnerAddress.present("উত্তর এখানে।"); assertEquals("মিস্টার ফাতেহি চৌধুরী নাইম স্যার, উত্তর এখানে।", a)
        assertEquals(a, OwnerAddress.present(a))   // never twice
        assertEquals("", OwnerAddress.present(""))
    }
}
