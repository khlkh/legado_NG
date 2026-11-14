package io.legado.app.help.config

import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookLanguageDetector
import io.legado.app.help.book.BookScriptClass
import io.legado.app.help.book.isImage

object ReadStyleLanguageBinder {
    fun apply(book: Book, extraSample: String? = null): Boolean {
        if (book.isImage || ReadBookConfig.onlyThisBook) return false
        val names = ReadBookConfig.configList.map { it.name }
        if (names.isEmpty()) return false
        val currentName = ReadBookConfig.durConfig.name
        val binding = book.config.readStyleBindingOrDefault()
        val decision = ReadStyleLanguagePolicy.decide(
            // FOLLOW ignores a stale remembered name; PINNED uses it.
            rememberedStyleName = binding.rememberedStyleName(book.config.readStyleName),
            languageHint = book.config.languageHint,
            sampleTexts = BookLanguageDetector.bookMetadataSamples(book) + listOfNotNull(extraSample),
            existingStyleNames = names,
            currentStyleName = currentName,
            bindings = ReadStyleLanguageMap.current(),
            fallbackScriptClass = BookScriptClass.fromStorage(book.config.scriptClass),
        )
        var persist = false
        if (decision.scriptClass != null &&
            book.config.scriptClass != decision.scriptClass.storageValue
        ) {
            book.config.scriptClass = decision.scriptClass.storageValue
            persist = true
        }
        // Auto-selection is not a user pin. Only pinCurrentStyle() writes readStyleName.
        val changed = ReadBookConfig.selectStyleByName(decision.styleNameToSelect)
        if (persist) book.save()
        return changed
    }

    /** 用户显式点选预设：固定到该预设。 */
    fun pinCurrentStyle(book: Book) {
        if (book.isImage) return
        val name = ReadBookConfig.durConfig.name.trim()
        if (name.isEmpty()) return
        if (book.config.readStyleBinding == ReadStyleBinding.PINNED.storageValue &&
            book.config.readStyleName == name
        ) return
        book.config.readStyleBinding = ReadStyleBinding.PINNED.storageValue
        book.config.readStyleName = name
        book.save()
    }

    /** 改为跟随语言默认；清除旧 pin，立即按当前语言映射重新选择。 */
    fun followLanguageDefault(book: Book): Boolean {
        if (book.isImage || ReadBookConfig.onlyThisBook) return false
        val changed = book.config.readStyleBinding != ReadStyleBinding.FOLLOW_LANGUAGE_DEFAULT.storageValue ||
            !book.config.readStyleName.isNullOrEmpty()
        book.config.readStyleBinding = ReadStyleBinding.FOLLOW_LANGUAGE_DEFAULT.storageValue
        book.config.readStyleName = null
        book.save()
        return apply(book) || changed
    }

    /**
     * 手动创建/删除/重命名预设后调用。只更新已经 PINNED 的书的缓存名字；
     * FOLLOW 的书不做隐式 pin。显式选预设请调用 [pinCurrentStyle]。
     */
    fun rememberCurrentStyle(book: Book) {
        if (book.isImage) return
        if (book.config.readStyleBindingOrDefault() != ReadStyleBinding.PINNED) return
        val name = ReadBookConfig.durConfig.name
        if (book.config.readStyleName != name) {
            book.config.readStyleName = name
            book.save()
        }
    }
}
