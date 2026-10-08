package com.winlator.cmod.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class WinComponentsSettingsTest {
    @Test
    fun `legacy settings get decoder defaults and codec choices survive component edits`() {
        val legacy = parseWinComponents("direct3d=0,directsound=1")
        assertEquals("gstreamer", legacy["builtinDecoder"])
        assertEquals("0", legacy["softwareDecoding"])
        val values = parseWinComponents("direct3d=1,builtinDecoder=ffmpeg,softwareDecoding=1").toMutableMap()
        values["directsound"] = "1"
        assertEquals(values, parseWinComponents(serializeWinComponents(values)))
        assertEquals("ffmpeg", values["builtinDecoder"])
        assertEquals("1", values["softwareDecoding"])
    }
}
