package io.legado.app.help.config

import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadStyleBindingTest {

    @Test
    fun `legacy remembered preset migrates to pinned`() {
        val config = Book.ReadConfig(readStyleName = "Eng")
        assertEquals(ReadStyleBinding.PINNED, config.readStyleBindingOrDefault())
    }

    @Test
    fun `legacy unnamed book follows language default`() {
        val config = Book.ReadConfig(readStyleName = null)
        assertEquals(ReadStyleBinding.FOLLOW_LANGUAGE_DEFAULT, config.readStyleBindingOrDefault())
        assertEquals(
            ReadStyleBinding.FOLLOW_LANGUAGE_DEFAULT,
            Book.ReadConfig(readStyleName = "  ").readStyleBindingOrDefault(),
        )
    }

    @Test
    fun `explicit binding wins over remembered name`() {
        val follow = Book.ReadConfig(
            readStyleName = "Eng",
            readStyleBinding = ReadStyleBinding.FOLLOW_LANGUAGE_DEFAULT.storageValue,
        )
        assertEquals(ReadStyleBinding.FOLLOW_LANGUAGE_DEFAULT, follow.readStyleBindingOrDefault())

        val pinned = Book.ReadConfig(
            readStyleName = null,
            readStyleBinding = ReadStyleBinding.PINNED.storageValue,
        )
        assertEquals(ReadStyleBinding.PINNED, pinned.readStyleBindingOrDefault())
    }

    @Test
    fun `unknown stored binding falls back to legacy inference`() {
        val config = Book.ReadConfig(
            readStyleName = "Eng",
            readStyleBinding = "future_value",
        )
        assertEquals(ReadStyleBinding.PINNED, config.readStyleBindingOrDefault())
    }

    @Test
    fun `follow ignores stale pinned name`() {
        assertEquals(
            null,
            ReadStyleBinding.FOLLOW_LANGUAGE_DEFAULT.rememberedStyleName("Eng"),
        )
        assertEquals(
            "Eng",
            ReadStyleBinding.PINNED.rememberedStyleName("Eng"),
        )
    }
}
