package com.ridereader.app

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("ride_reader", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", true)
        set(v) = sp.edit().putBoolean("enabled", v).apply()

    /** कॉमा या नई लाइन से अलग किए हुए एरिया, जहाँ से पिकअप नहीं लेना */
    var blockedPickup: String
        get() = sp.getString("blockedPickup", "") ?: ""
        set(v) = sp.edit().putString("blockedPickup", v).apply()

    /** कॉमा या नई लाइन से अलग किए हुए एरिया, जहाँ ड्रॉप नहीं लेना */
    var blockedDrop: String
        get() = sp.getString("blockedDrop", "") ?: ""
        set(v) = sp.edit().putString("blockedDrop", v).apply()

    /** इससे ज़्यादा दूर पिकअप हो तो चेतावनी. 0 = बंद */
    var maxPickupKm: Float
        get() = sp.getFloat("maxPickupKm", 0f)
        set(v) = sp.edit().putFloat("maxPickupKm", v).apply()

    var speechRate: Float
        get() = sp.getFloat("speechRate", 1.0f)
        set(v) = sp.edit().putFloat("speechRate", v).apply()

    var hindi: Boolean
        get() = sp.getBoolean("hindi", true)
        set(v) = sp.edit().putBoolean("hindi", v).apply()

    companion object {
        fun parseList(raw: String): List<String> =
            raw.split(',', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }
}
