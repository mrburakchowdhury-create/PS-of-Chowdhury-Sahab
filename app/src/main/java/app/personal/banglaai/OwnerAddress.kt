package app.personal.banglaai

/**
 * §13: how the assistant addresses its owner. ONE constant, applied once at the start of an assistant response in the presentation / speech-preparation
 * layer — never inside prompts, original document text, quotations, translations, exports, code or notifications, so those stay exactly as they were.
 */
object OwnerAddress {
    const val OWNER_ADDRESS_BN = app.personal.banglaai.security.OwnerIdentity.OWNER_NAME_BN

    /** "<address>, <text>" — once; text that already starts with the address (or is blank) is returned unchanged. */
    fun present(text: String): String {
        val t = text.trimStart()
        return if (t.isBlank() || t.startsWith(OWNER_ADDRESS_BN)) text else "$OWNER_ADDRESS_BN, $t"
    }
}
