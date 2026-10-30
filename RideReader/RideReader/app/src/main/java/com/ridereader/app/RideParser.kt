package com.ridereader.app

data class Ride(
    val pickupKm: Double,
    val pickupAddr: String,
    val dropKm: Double,
    val dropAddr: String
) {
    /** एक ही राइड को पहचानने के लिए */
    val key: String get() = "$pickupKm|$pickupAddr|$dropKm|$dropAddr"

    /** " - " से पहले वाला छोटा नाम, जैसे "ISBT" */
    val pickupName: String get() = shortName(pickupAddr)
    val dropName: String get() = shortName(dropAddr)

    private fun shortName(addr: String): String = addr.substringBefore(" - ").trim()
}

/**
 * Rapido के राइड कार्ड का टेक्स्ट (ऊपर से नीचे के क्रम में) लेकर राइड निकालता है.
 *
 * कार्ड का क्रम: [Bike Boost] , पिकअप km , पिकअप पता , ड्रॉप km , ड्रॉप पता , "Accept"
 * हर "Accept" पर एक कार्ड खत्म माना जाता है, इसलिए एक साथ कई कार्ड हों तो भी अलग-अलग निकलते हैं.
 */
object RideParser {
    private val kmRegex = Regex("""^\s*(\d+(?:\.\d+)?)\s*km\b\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(texts: List<String>): List<Ride> {
        val rides = mutableListOf<Ride>()
        val buffer = mutableListOf<String>()
        for (raw in texts) {
            val t = raw.trim()
            if (t.isEmpty()) continue
            if (t.equals("Accept", ignoreCase = true)) {
                parseCard(buffer)?.let { rides.add(it) }
                buffer.clear()
            } else {
                buffer.add(t)
            }
        }
        return rides
    }

    private fun parseCard(items: List<String>): Ride? {
        val kms = mutableListOf<Double>()
        val addrs = mutableListOf<StringBuilder>()
        for (item in items) {
            val m = kmRegex.matchEntire(item)
            if (m != null) {
                kms.add(m.groupValues[1].toDouble())
                val sb = StringBuilder()
                val rest = m.groupValues[2].trim()
                if (rest.isNotEmpty()) sb.append(rest)
                addrs.add(sb)
            } else if (addrs.isNotEmpty()) {
                // पिछले km के बाद आने वाला टेक्स्ट उसी का पता है
                val sb = addrs.last()
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(item)
            }
        }
        if (kms.size < 2) return null
        return Ride(
            pickupKm = kms[0],
            pickupAddr = addrs[0].toString().trim(),
            dropKm = kms[1],
            dropAddr = addrs[1].toString().trim()
        )
    }
}
