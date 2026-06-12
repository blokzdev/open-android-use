package dev.openandroiduse.companion.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyMapperTest {

    @Test
    fun mapsSupportedKeysCaseInsensitively() {
        assertEquals(KeyMapper.KeyAction.ImeEnter, KeyMapper.map("Return"))
        assertEquals(KeyMapper.KeyAction.ImeEnter, KeyMapper.map("ENTER"))
        assertEquals(KeyMapper.KeyAction.ImeEnter, KeyMapper.map("KP_Enter"))
        assertEquals(KeyMapper.KeyAction.Back, KeyMapper.map("Back"))
        assertEquals(KeyMapper.KeyAction.Back, KeyMapper.map("escape"))
        assertEquals(KeyMapper.KeyAction.Home, KeyMapper.map("Home"))
        assertEquals(KeyMapper.KeyAction.Recents, KeyMapper.map("recents"))
        assertEquals(KeyMapper.KeyAction.DeleteBackward, KeyMapper.map("BackSpace"))
    }

    @Test
    fun unsupportedKeysReturnInformativeError() {
        val action = KeyMapper.map("super+c")
        assertTrue(action is KeyMapper.KeyAction.Unsupported)
        assertTrue((action as KeyMapper.KeyAction.Unsupported).message.contains("super+c"))
        assertTrue(action.message.contains("type_text"))
    }
}
