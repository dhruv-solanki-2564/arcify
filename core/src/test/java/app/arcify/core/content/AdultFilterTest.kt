package app.arcify.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultFilterTest {

    private val domains = setOf("adult-example.com", "cdn.other-example.net")

    @Test
    fun listedDomainsAndTheirSubdomainsAreBlocked() {
        assertTrue(AdultFilter.isBlocked("adult-example.com", domains, emptySet()))
        assertTrue(AdultFilter.isBlocked("www.adult-example.com", domains, emptySet()))
        assertTrue(AdultFilter.isBlocked("m.video.adult-example.com", domains, emptySet()))
        assertTrue(AdultFilter.isBlocked("cdn.other-example.net", domains, emptySet()))
    }

    @Test
    fun parentsOfListedSubdomainsAndLookalikesAreNot() {
        assertFalse(AdultFilter.isBlocked("other-example.net", domains, emptySet()))
        assertFalse(AdultFilter.isBlocked("notadult-example.com", domains, emptySet()))
        assertFalse(AdultFilter.isBlocked("wikipedia.org", domains, emptySet()))
    }

    @Test
    fun exceptionsWinAndCoverSubdomains() {
        assertFalse(AdultFilter.isBlocked("video.adult-example.com", domains, setOf("adult-example.com")))
    }

    @Test
    fun suffixesStopBeforeTheTopLevelDomain() {
        assertEquals(listOf("a.b.example.com", "b.example.com", "example.com"), AdultFilter.suffixes("a.b.example.com"))
        assertEquals(emptyList<String>(), AdultFilter.suffixes("localhost"))
    }

    @Test
    fun exceptionsAreNormalised() {
        assertEquals("example.com", AdultFilter.normalizeException("https://www.Example.com/page"))
        assertNull(AdultFilter.normalizeException("not a site"))
    }

    @Test
    fun aLockedFilterCannotBeLoosened() {
        assertFalse(AdultFilter.canLoosen(AdultFilterSettings(enabled = true, strictUntil = 1_000), now = 500))
        assertTrue(AdultFilter.canLoosen(AdultFilterSettings(enabled = true, strictUntil = 1_000), now = 1_000))
    }
}
