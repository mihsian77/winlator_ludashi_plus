package com.winlator.cmod.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.winlator.cmod.R
import com.winlator.cmod.container.Container

internal fun parseWinComponents(raw: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    for (source in listOf(Container.DEFAULT_WINCOMPONENTS, raw)) {
        source.split(',').forEach { token ->
            val pair = token.split('=', limit = 2)
            if (pair.size == 2 && pair[0].isNotBlank()) result[pair[0]] = pair[1]
        }
    }
    return result
}

internal fun serializeWinComponents(values: Map<String, String>): String =
    values.entries.joinToString(",") { "${it.key}=${it.value}" }

@Composable
internal fun DecoderSettings(values: MutableMap<String, String>, onChanged: () -> Unit) {
    val decoders = listOf("GStreamer", "FFmpeg")
    SettingChoice(stringResource(R.string.builtin_decoder),
        if (values["builtinDecoder"] == "ffmpeg") decoders[1] else decoders[0], decoders) {
        values["builtinDecoder"] = if (it == decoders[1]) "ffmpeg" else "gstreamer"
        onChanged()
    }
    SettingsDivider()
    SettingToggle(stringResource(R.string.software_decoding), values["softwareDecoding"] == "1") {
        values["softwareDecoding"] = if (it) "1" else "0"
        onChanged()
    }
}
