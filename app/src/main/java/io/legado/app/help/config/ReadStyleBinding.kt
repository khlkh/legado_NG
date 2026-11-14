package io.legado.app.help.config

import io.legado.app.data.entities.Book

/**
 * 本书的预设绑定方式。
 *
 * 旧数据没有该字段：有 readStyleName 视为 PINNED（保留用户已记住的预设），
 * 没有 readStyleName 视为 FOLLOW_LANGUAGE_DEFAULT。
 */
enum class ReadStyleBinding(val storageValue: String) {
    FOLLOW_LANGUAGE_DEFAULT("follow_language_default"),
    PINNED("pinned"),
    ;

    companion object {
        fun fromStorage(value: String?): ReadStyleBinding? =
            entries.firstOrNull { it.storageValue == value }
    }
}

fun Book.ReadConfig.readStyleBindingOrDefault(): ReadStyleBinding {
    ReadStyleBinding.fromStorage(readStyleBinding)?.let { return it }
    return if (readStyleName.isNullOrBlank()) {
        ReadStyleBinding.FOLLOW_LANGUAGE_DEFAULT
    } else {
        ReadStyleBinding.PINNED
    }
}

/** FOLLOW ignores the stored name; only a PINNED book treats it as authoritative. */
fun ReadStyleBinding.rememberedStyleName(storedName: String?): String? =
    if (this == ReadStyleBinding.PINNED) storedName else null
