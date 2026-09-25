package com.tappony.android.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.tappony.core.NdefRecord
import com.tappony.core.TagFamily
import com.tappony.core.TagReading
import com.tappony.core.Uid
import java.io.IOException

/**
 * Turns an Android [Tag] into the core's platform-neutral [TagReading].
 *
 * Runs on the reader-mode binder thread. Every tech is connected, used and
 * closed in turn because Android allows one connected tech per tag at a time.
 * Each extended command is best-effort: a tag that refuses GET_VERSION or
 * READ_SIG still produces a reading with the identifiers it did give.
 */
object AndroidTagReader {

    private const val TIMEOUT_MS = 400

    fun read(tag: Tag, extendedReads: Boolean): TagReading {
        val techs = tag.techList.toSet()
        val has = { cls: Class<*> -> cls.name in techs }
        val id = tag.id ?: ByteArray(0)

        val family = when {
            has(NfcF::class.java) -> TagFamily.FELICA
            has(NfcV::class.java) -> TagFamily.ISO15693
            has(NfcB::class.java) -> TagFamily.ISO7816_B
            else -> TagFamily.MIFARE
        }
        var tagType = when {
            family == TagFamily.FELICA -> "felica"
            family == TagFamily.ISO15693 -> "iso15693"
            family == TagFamily.ISO7816_B -> "iso7816"
            has(MifareUltralight::class.java) -> "mifare_ultralight"
            has(MifareClassic::class.java) -> "mifare_classic"
            has(IsoDep::class.java) -> "iso7816"
            has(NfcA::class.java) -> "unknown"
            else -> "unknown"
        }

        var chip = ""
        var signature: ByteArray? = null
        var counter: Int? = null
        var atqa: ByteArray? = null
        var sak: ByteArray? = null
        var dsfid: ByteArray? = null
        var afi: ByteArray? = null
        var blockSize: Int? = null
        var blockCount: Int? = null
        var pmm: ByteArray? = null
        var systemCode: ByteArray? = null
        var historical: ByteArray? = null
        var appData: ByteArray? = null

        NfcA.get(tag)?.let {
            atqa = it.atqa
            sak = byteArrayOf(it.sak.toByte())
        }
        NfcF.get(tag)?.let {
            pmm = it.manufacturer
            systemCode = it.systemCode
        }
        NfcB.get(tag)?.let { appData = it.applicationData }
        NfcV.get(tag)?.let { dsfid = byteArrayOf(it.dsfId) }

        if (extendedReads) {
            if (has(MifareUltralight::class.java)) {
                MifareUltralight.get(tag)?.let { ul ->
                    withTech(ul) {
                        ul.timeout = TIMEOUT_MS
                        runCatching { ul.transceive(byteArrayOf(0x60)) }.getOrNull()?.let { chip = Uid.chipFromGetVersion(it) }
                        if (chip.isNotEmpty()) {
                            signature = runCatching { ul.transceive(byteArrayOf(0x3C, 0x00)) }.getOrNull()?.takeIf { it.size == 32 }
                            counter = runCatching { ul.transceive(byteArrayOf(0x39, 0x02)) }.getOrNull()
                                ?.takeIf { it.size == 3 }
                                ?.let { (it[0].toInt() and 0xFF) or ((it[1].toInt() and 0xFF) shl 8) or ((it[2].toInt() and 0xFF) shl 16) }
                        }
                    }
                }
            }
            if (has(IsoDep::class.java)) {
                IsoDep.get(tag)?.let { dep ->
                    historical = dep.historicalBytes ?: dep.hiLayerResponse
                    withTech(dep) {
                        dep.timeout = TIMEOUT_MS * 2
                        val resp = runCatching { dep.transceive(byteArrayOf(0x90.toByte(), 0x60, 0x00, 0x00, 0x00)) }.getOrNull()
                        if (resp != null && resp.size >= 9) {
                            val d = Uid.chipFromDesfireVersion(resp.copyOfRange(0, resp.size - 2))
                            if (d.isNotEmpty()) {
                                chip = d
                                if (family == TagFamily.MIFARE) tagType = "mifare_desfire"
                            }
                        }
                    }
                }
            }
            if (family == TagFamily.ISO15693) {
                NfcV.get(tag)?.let { v ->
                    withTech(v) {
                        val info = runCatching { v.transceive(byteArrayOf(0x02, 0x2B)) }.getOrNull()
                        if (info != null && info.size >= 10 && info[0].toInt() == 0) {
                            val flags = info[1].toInt() and 0xFF
                            var i = 10
                            if (flags and 0x01 != 0 && i < info.size) { dsfid = byteArrayOf(info[i]); i++ }
                            if (flags and 0x02 != 0 && i < info.size) { afi = byteArrayOf(info[i]); i++ }
                            if (flags and 0x04 != 0 && i + 1 < info.size) {
                                blockCount = (info[i].toInt() and 0xFF) + 1
                                blockSize = (info[i + 1].toInt() and 0x1F) + 1
                            }
                        }
                    }
                }
            }
        }

        val records = readNdef(tag)
        return TagReading(
            family = family,
            tagType = tagType,
            identifier = id,
            chip = chip,
            signature = signature,
            counter = counter,
            atqa = atqa,
            sak = sak,
            dsfid = dsfid,
            afi = afi,
            blockSize = blockSize,
            blockCount = blockCount,
            pmm = pmm,
            systemCode = systemCode,
            historicalBytes = historical,
            applicationData = appData,
            ndef = records,
        )
    }

    private fun readNdef(tag: Tag): List<NdefRecord> {
        val ndef = Ndef.get(tag) ?: return emptyList()
        val msg = ndef.cachedNdefMessage ?: run {
            var m: android.nfc.NdefMessage? = null
            withTech(ndef) { m = runCatching { ndef.ndefMessage }.getOrNull() }
            m
        } ?: return emptyList()
        return msg.records.map { NdefRecord(it.tnf.toInt(), it.type ?: ByteArray(0), it.id ?: ByteArray(0), it.payload ?: ByteArray(0)) }
    }

    private inline fun withTech(tech: android.nfc.tech.TagTechnology, block: () -> Unit) {
        try {
            tech.connect()
            block()
        } catch (_: IOException) {
        } catch (_: SecurityException) {
            // Tag moved away or was replaced mid-read.
        } finally {
            runCatching { tech.close() }
        }
    }
}
