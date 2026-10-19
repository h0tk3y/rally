import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.h0tk3y.rally.android.PreferenceRepository
import com.h0tk3y.rally.android.SpeedButtons
import com.h0tk3y.rally.android.SpeedButtonsParseResult.Empty
import com.h0tk3y.rally.android.SpeedButtonsParseResult.NotAWholeNumber
import com.h0tk3y.rally.android.SpeedButtonsParseResult.OutOfRange
import com.h0tk3y.rally.android.SpeedButtonsParseResult.TooMany
import com.h0tk3y.rally.android.SpeedButtonsParseResult.Valid
import com.h0tk3y.rally.android.decodeStoredSpeedButtons
import com.h0tk3y.rally.android.encodeSpeedButtons
import com.h0tk3y.rally.android.parseSpeedButtonsInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeedButtonsTest {
    @Test
    fun `speeds are separated by any whitespace or punctuation other than dots`() {
        assertEquals(Valid(listOf(20, 40, 60, 80)), parseSpeedButtonsInput("20 40, 60; 80"))
        assertEquals(Valid(listOf(20, 40, 60)), parseSpeedButtonsInput("  20\t\n40/60!  "))
        assertEquals(Valid(listOf(20, 40, 60)), parseSpeedButtonsInput("20\u00A040–60"))
        assertEquals(Valid(listOf(20, 40)), parseSpeedButtonsInput(",,20;;;40,,"))
        assertEquals(Valid(listOf(20, 40)), parseSpeedButtonsInput("20,40"))
    }

    @Test
    fun `a single speed is valid`() {
        assertEquals(Valid(listOf(7)), parseSpeedButtonsInput("7"))
    }

    @Test
    fun `fractional speeds and dots are not whole numbers`() {
        assertEquals(NotAWholeNumber("42.5"), parseSpeedButtonsInput("20 42.5 60"))
        assertEquals(NotAWholeNumber("20."), parseSpeedButtonsInput("20. 40"))
        assertEquals(NotAWholeNumber(".5"), parseSpeedButtonsInput(".5"))
        assertEquals(NotAWholeNumber("."), parseSpeedButtonsInput("20 . 40"))
        assertEquals(NotAWholeNumber("..."), parseSpeedButtonsInput("..."))
    }

    @Test
    fun `dot-like characters are not separators either`() {
        listOf("．", "﹒", "․", "‥", "…", "·", "٫", "。", "｡").forEach { dot ->
            assertEquals(NotAWholeNumber("42${dot}5"), parseSpeedButtonsInput("42${dot}5"), "dot: '$dot'")
        }
    }

    @Test
    fun `a comma between digits separates two speeds`() {
        assertEquals(Valid(listOf(42, 5)), parseSpeedButtonsInput("42,5"))
    }

    @Test
    fun `a minus is a separator too`() {
        assertEquals(Valid(listOf(20)), parseSpeedButtonsInput("-20"))
        assertEquals(Valid(listOf(20, 40)), parseSpeedButtonsInput("20-40"))
    }

    @Test
    fun `the order is kept as typed`() {
        assertEquals(Valid(listOf(60, 20, 110, 5)), parseSpeedButtonsInput("60 20 110 5"))
    }

    @Test
    fun `repeated speeds are kept only at the first place`() {
        assertEquals(Valid(listOf(60, 20, 40)), parseSpeedButtonsInput("60 20 60 40 20"))
        assertEquals(Valid(listOf(5)), parseSpeedButtonsInput("5 05 005"))
    }

    @Test
    fun `leading zeros are allowed within three digits`() {
        assertEquals(Valid(listOf(5, 50)), parseSpeedButtonsInput("05 050"))
    }

    @Test
    fun `empty input or only separators is invalid`() {
        assertEquals(Empty, parseSpeedButtonsInput(""))
        assertEquals(Empty, parseSpeedButtonsInput("   "))
        assertEquals(Empty, parseSpeedButtonsInput(" ,; -/"))
    }

    @Test
    fun `zero is not a positive speed`() {
        assertEquals(OutOfRange("0"), parseSpeedButtonsInput("20 0 40"))
        assertEquals(OutOfRange("000"), parseSpeedButtonsInput("000"))
    }

    @Test
    fun `numbers of four or more digits are out of range`() {
        assertEquals(OutOfRange("1000"), parseSpeedButtonsInput("20 1000"))
        assertEquals(OutOfRange("0050"), parseSpeedButtonsInput("0050"))
        assertEquals(OutOfRange("99999999999999999999"), parseSpeedButtonsInput("99999999999999999999"))
        assertEquals(Valid(listOf(999)), parseSpeedButtonsInput("999"))
    }

    @Test
    fun `letters and other non-digits are invalid and the first one is reported`() {
        assertEquals(NotAWholeNumber("abc"), parseSpeedButtonsInput("20 abc 1000"))
        assertEquals(NotAWholeNumber("4O"), parseSpeedButtonsInput("4O"))
        assertEquals(NotAWholeNumber("20km"), parseSpeedButtonsInput("20km"))
        // Non-ASCII digits are not accepted, they would not be shown as typed
        assertEquals(NotAWholeNumber("٤٥"), parseSpeedButtonsInput("٤٥"))
    }

    @Test
    fun `too many speeds are invalid but repeats are not counted`() {
        val max = (1..SpeedButtons.MAX_COUNT).joinToString(" ")
        assertEquals(Valid((1..SpeedButtons.MAX_COUNT).toList()), parseSpeedButtonsInput(max))
        assertEquals(Valid((1..SpeedButtons.MAX_COUNT).toList()), parseSpeedButtonsInput("$max 1 2 3"))
        assertEquals(TooMany(SpeedButtons.MAX_COUNT + 1), parseSpeedButtonsInput("$max ${SpeedButtons.MAX_COUNT + 1}"))
    }

    @Test
    fun `the default list round trips through the stored form`() {
        val encoded = encodeSpeedButtons(SpeedButtons.DEFAULT)
        assertEquals("5 10 20 30 40 50 60 70 80 90 100 110", encoded)
        assertEquals(SpeedButtons.DEFAULT, decodeStoredSpeedButtons(encoded))
        assertEquals(Valid(SpeedButtons.DEFAULT), parseSpeedButtonsInput(encoded))
    }

    @Test
    fun `a missing, empty or corrupted stored value gives the default list`() {
        listOf(
            null, "", " ", "20  40", " 20", "20,40", "42.5 60", "20 abc", "0", "20 0", "1000", "-20",
            (1..SpeedButtons.MAX_COUNT + 1).joinToString(" ")
        ).forEach {
            assertEquals(SpeedButtons.DEFAULT, decodeStoredSpeedButtons(it), "stored: '$it'")
        }
    }

    @Test
    fun `a valid stored value is read as is`() {
        assertEquals(listOf(60, 20, 110), decodeStoredSpeedButtons("60 20 110"))
        assertEquals(listOf(60, 20), decodeStoredSpeedButtons("60 20 60"))
    }

    private val key = stringPreferencesKey("speedButtons")

    @Test
    fun `preferences without the value give the default list`() = runTest {
        assertEquals(SpeedButtons.DEFAULT, repository(emptyPreferences()).userPreferencesFlow.first().speedButtons)
    }

    @Test
    fun `preferences with a corrupted value give the default list`() = runTest {
        assertEquals(SpeedButtons.DEFAULT, repository(mutablePreferencesOf(key to "20 abc")).userPreferencesFlow.first().speedButtons)
    }

    @Test
    fun `preferences with a value of another type give the default list`() = runTest {
        val prefs = mutablePreferencesOf(intPreferencesKey("speedButtons") to 20)
        assertEquals(SpeedButtons.DEFAULT, repository(prefs).userPreferencesFlow.first().speedButtons)
    }

    @Test
    fun `saved speeds are read back`() = runTest {
        val repository = repository(emptyPreferences())
        repository.saveSpeedButtons(listOf(60, 20, 110))
        assertEquals(listOf(60, 20, 110), repository.userPreferencesFlow.first().speedButtons)
    }

    @Test
    fun `an invalid list is not saved`() = runTest {
        val repository = repository(emptyPreferences())
        repository.saveSpeedButtons(listOf(60, 20))
        listOf(emptyList(), listOf(0), listOf(1000), listOf(20, 20), (1..SpeedButtons.MAX_COUNT + 1).toList()).forEach {
            repository.saveSpeedButtons(it)
            assertEquals(listOf(60, 20), repository.userPreferencesFlow.first().speedButtons, "saving $it")
        }
    }

    private fun repository(initial: Preferences) = PreferenceRepository(FakeDataStore(initial))

    private class FakeDataStore(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> get() = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }
}
