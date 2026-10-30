package com.ridereader.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var statusView: TextView
    private lateinit var pickupEdit: EditText
    private lateinit var dropEdit: EditText
    private lateinit var maxKmEdit: EditText
    private lateinit var rateText: TextView
    private var testTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(title("Ride Reader"))

        statusView = TextView(this).apply { textSize = 16f; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(statusView)

        root.addView(Button(this).apply {
            text = "Accessibility सेटिंग खोलें"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        root.addView(Switch(this).apply {
            text = "राइड बोलना चालू"
            textSize = 16f
            isChecked = prefs.enabled
            setPadding(0, dp(16), 0, dp(8))
            setOnCheckedChangeListener { _, c -> prefs.enabled = c }
        })

        root.addView(Switch(this).apply {
            text = "हिंदी में बोलो (बंद = English)"
            textSize = 16f
            isChecked = prefs.hindi
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, c -> prefs.hindi = c }
        })

        root.addView(label("ये पिकअप एरिया नहीं चाहिए (कॉमा से अलग करें)"))
        pickupEdit = edit("जैसे: Berkheda, ISBT", prefs.blockedPickup, multiline = true)
        root.addView(pickupEdit)

        root.addView(label("ये ड्रॉप एरिया नहीं चाहिए (कॉमा से अलग करें)"))
        dropEdit = edit("जैसे: Tulsi Nagar, Arera Hills", prefs.blockedDrop, multiline = true)
        root.addView(dropEdit)

        root.addView(label("पिकअप इतने किमी से दूर हो तो चेतावनी (0 = बंद)"))
        maxKmEdit = edit("जैसे: 3", if (prefs.maxPickupKm > 0f) prefs.maxPickupKm.toString() else "", multiline = false)
        maxKmEdit.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        root.addView(maxKmEdit)

        rateText = label("")
        root.addView(rateText)
        root.addView(SeekBar(this).apply {
            max = 15 // 0.5x से 2.0x
            progress = ((prefs.speechRate - 0.5f) / 0.1f).toInt().coerceIn(0, 15)
            updateRateLabel(prefs.speechRate)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val r = 0.5f + p * 0.1f
                    prefs.speechRate = r
                    updateRateLabel(r)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        })

        root.addView(Button(this).apply {
            text = "सेव करें"
            setOnClickListener { save() }
        })
        root.addView(Button(this).apply {
            text = "आवाज़ टेस्ट करें"
            setOnClickListener { testVoice() }
        })

        root.addView(TextView(this).apply {
            text = "यह ऐप सिर्फ़ पढ़ती और बोलती है. यह राइड accept नहीं करती, और इसमें इंटरनेट permission नहीं है."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val on = isServiceEnabled()
        statusView.text = if (on) "✅ Accessibility चालू है" else "❌ Accessibility बंद है. नीचे बटन से 'Ride Reader' चालू करें."
        statusView.setTextColor(if (on) Color.parseColor("#1B7F3B") else Color.parseColor("#B00020"))
    }

    override fun onDestroy() {
        testTts?.shutdown()
        super.onDestroy()
    }

    private fun save() {
        prefs.blockedPickup = pickupEdit.text.toString()
        prefs.blockedDrop = dropEdit.text.toString()
        prefs.maxPickupKm = maxKmEdit.text.toString().trim().toFloatOrNull() ?: 0f
        Toast.makeText(this, "सेव हो गया", Toast.LENGTH_SHORT).show()
    }

    private fun testVoice() {
        save()
        testTts?.shutdown()
        testTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val hindi = prefs.hindi
                testTts?.setLanguage(if (hindi) Locale("hi", "IN") else Locale("en", "IN"))
                testTts?.setSpeechRate(prefs.speechRate)
                val msg = if (hindi) "ISBT, 0.9 किलोमीटर. ड्रॉप तुलसी नगर, 7.3 किलोमीटर."
                else "ISBT, 0.9 kilometers. Drop Tulsi Nagar, 7.3 kilometers."
                testTts?.speak(msg, TextToSpeech.QUEUE_FLUSH, null, "test")
            } else {
                Toast.makeText(this, "फोन में Text-to-Speech चालू नहीं है", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = "$packageName/${RideAccessibilityService::class.java.name}"
        val meShort = "$packageName/.RideAccessibilityService"
        return enabled.split(':').any { it.equals(me, true) || it.equals(meShort, true) }
    }

    private fun SeekBar.updateRateLabel(r: Float) {
        rateText.text = "बोलने की स्पीड: %.1fx".format(r)
    }

    private fun title(t: String) = TextView(this).apply { text = t; textSize = 24f; setTextColor(Color.BLACK) }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setPadding(0, dp(16), 0, dp(4))
    }

    private fun edit(hint: String, value: String, multiline: Boolean) = EditText(this).apply {
        this.hint = hint
        setText(value)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        if (multiline) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
