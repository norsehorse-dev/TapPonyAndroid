package com.tappony.core

/**
 * Tag families as the core sees them. The wire names are the ones used in
 * fixtures/uid_vectors.json and variables_vectors.json.
 */
enum class TagFamily(val wire: String) {
    MIFARE("mifare"),          // ISO 14443-A, NFC-A (NTAG, Ultralight, DESFire, Plus, Classic on Android)
    ISO15693("iso15693"),      // NFC-V (ICODE, ST, Infineon)
    FELICA("felica"),          // NFC-F
    ISO7816_A("iso7816_a"),    // ISO 14443-4 Type A smart card
    ISO7816_B("iso7816_b");    // ISO 14443-4 Type B smart card (PUPI)

    companion object {
        fun fromWire(s: String): TagFamily = values().first { it.wire == s }
    }
}

/** UID canonicalization, formats, manufacturer and chip identification. PROFILE_SCHEMA.md section 8. */
object Uid {

    /** ISO/IEC 7816-6 IC manufacturer codes, the well-established part of the table. */
    private val MANUFACTURERS = mapOf(
        0x01 to "Motorola", 0x02 to "STMicroelectronics", 0x03 to "Hitachi", 0x04 to "NXP",
        0x05 to "Infineon", 0x06 to "Cylink", 0x07 to "Texas Instruments", 0x08 to "Fujitsu",
        0x09 to "Matsushita", 0x0A to "NEC", 0x0B to "Oki", 0x0C to "Toshiba", 0x0D to "Mitsubishi",
        0x0E to "Samsung", 0x0F to "Hynix", 0x10 to "LG", 0x11 to "Emosyn-EM", 0x12 to "INSIDE Technology",
        0x13 to "ORGA", 0x14 to "Sharp", 0x15 to "Atmel", 0x16 to "EM Microelectronic",
    )

    fun canonical(family: TagFamily, raw: ByteArray): ByteArray {
        if (family == TagFamily.ISO15693 && raw.size > 1 &&
            (raw[0].toInt() and 0xFF) != 0xE0 && (raw[raw.size - 1].toInt() and 0xFF) == 0xE0
        ) return raw.reversedArray()
        return raw.copyOf()
    }

    fun manufacturer(family: TagFamily, canonical: ByteArray): String = when {
        family == TagFamily.FELICA -> "Sony"
        family == TagFamily.ISO15693 && canonical.size >= 2 && (canonical[0].toInt() and 0xFF) == 0xE0 ->
            MANUFACTURERS[canonical[1].toInt() and 0xFF] ?: ""
        (family == TagFamily.MIFARE || family == TagFamily.ISO7816_A) && canonical.size == 7 ->
            MANUFACTURERS[canonical[0].toInt() and 0xFF] ?: ""
        else -> ""
    }

    fun isRandom(family: TagFamily, canonical: ByteArray): Boolean =
        (family == TagFamily.MIFARE || family == TagFamily.ISO7816_A) && canonical.size == 4 && (canonical[0].toInt() and 0xFF) == 0x08

    /** The identity variables: uid, uid_colon, uid_dec, uid_rev, uid_len, random_uid, manufacturer. */
    fun variables(family: TagFamily, raw: ByteArray): Map<String, String> {
        val b = canonical(family, raw)
        return linkedMapOf(
            "uid" to Encoding.hexUpper(b),
            "uid_colon" to Encoding.colon(b),
            "uid_dec" to Encoding.decimal(b),
            "uid_rev" to Encoding.hexUpper(b.reversedArray()),
            "uid_len" to b.size.toString(),
            "random_uid" to if (isRandom(family, b)) "true" else "false",
            "manufacturer" to manufacturer(family, b),
        )
    }

    /** NTAG21x / Ultralight EV1 GET_VERSION (0x60) response, 8 bytes. Empty when unrecognized. */
    fun chipFromGetVersion(resp: ByteArray): String {
        if (resp.size != 8 || (resp[1].toInt() and 0xFF) != 0x04) return ""
        val type = resp[2].toInt() and 0xFF
        val sub = resp[3].toInt() and 0xFF
        val size = resp[6].toInt() and 0xFF
        if (type == 0x04 && sub == 0x02) return when (size) {
            0x0F -> "NTAG213"; 0x11 -> "NTAG215"; 0x13 -> "NTAG216"; else -> ""
        }
        if (type == 0x03 && sub == 0x01) return when (size) {
            0x0B, 0x0E -> "Ultralight EV1"; else -> ""
        }
        return ""
    }

    /** DESFire GetVersion first frame (hardware info). Empty when unrecognized. Confirm on hardware in the spike. */
    fun chipFromDesfireVersion(hw: ByteArray): String {
        if (hw.size < 4 || (hw[0].toInt() and 0xFF) != 0x04 || (hw[1].toInt() and 0xFF) != 0x01) return ""
        return when (hw[3].toInt() and 0xFF) {
            0x00 -> "DESFire"; 0x01 -> "DESFire EV1"; 0x12 -> "DESFire EV2"; 0x33 -> "DESFire EV3"; else -> ""
        }
    }
}
