package com.ridereader.app

import android.accessibilityservice.AccessibilityService
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Rapido की स्क्रीन (और उसके ऊपर तैरती विंडो) का टेक्स्ट पढ़ती है और राइड एक-एक करके बोलती है.
 * यह किसी बटन को क्लिक नहीं करती, यानी राइड अपने-आप accept नहीं होती.
 */
class RideAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val queue = ArrayDeque<Ride>()
    private val spokenKeys = HashSet<String>()
    private val lastSeen = HashMap<String, Long>()
    private var speaking = false

    private val scanRunnable = Runnable { scan() }
    private val tickRunnable = object : Runnable {
        override fun run() {
            scan()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { handler.post { speaking = false; speakNext() } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { handler.post { speaking = false; speakNext() } }
                })
                ttsReady = true
            }
        }
        handler.removeCallbacks(tickRunnable)
        handler.post(tickRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // बार-बार आने वाले events को जोड़कर एक बार scan करो
        handler.removeCallbacks(scanRunnable)
        handler.postDelayed(scanRunnable, DEBOUNCE_MS)
    }

    override fun onInterrupt() {
        tts?.stop()
        speaking = false
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun scan() {
        if (!::prefs.isInitialized) return
        val now = SystemClock.elapsedRealtime()
        if (!prefs.enabled) {
            queue.clear()
            return
        }

        val rides = mutableListOf<Ride>()
        try {
            val roots = mutableListOf<AccessibilityNodeInfo>()
            windows?.forEach { w -> w.root?.let { roots.add(it) } }
            if (roots.isEmpty()) rootInActiveWindow?.let { roots.add(it) }

            for (root in roots) {
                val pkg = root.packageName?.toString()?.lowercase() ?: continue
                if (!pkg.contains("rapido")) continue
                val texts = ArrayList<String>()
                collectTexts(root, texts, 0)
                rides.addAll(RideParser.parse(texts))
            }
        } catch (_: Exception) {
            return
        }

        // अभी स्क्रीन पर दिख रही राइड
        for (ride in rides) {
            lastSeen[ride.key] = now
            if (spokenKeys.add(ride.key)) {
                queue.addLast(ride)
            }
        }

        // जो राइड स्क्रीन से गायब हुई, उसे भूल जाओ, ताकि नई राइड की तरह गिनी जाए
        val gone = lastSeen.filter { now - it.value > FORGET_MS }.keys
        gone.forEach { lastSeen.remove(it); spokenKeys.remove(it) }

        speakNext()
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 40) return
        val t = node.text?.toString() ?: node.contentDescription?.toString()
        if (!t.isNullOrBlank()) out.add(t)
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), out, depth + 1)
        }
    }

    private fun speakNext() {
        if (speaking || !ttsReady) return
        val now = SystemClock.elapsedRealtime()
        while (queue.isNotEmpty()) {
            val ride = queue.removeFirst()
            // लाइन में रुकी राइड अगर अब स्क्रीन पर नहीं है, तो उसे छोड़ दो
            val seenAt = lastSeen[ride.key] ?: continue
            if (now - seenAt > STALE_MS) continue
            speak(ride)
            return
        }
    }

    private fun speak(ride: Ride) {
        val engine = tts ?: return
        val hindi = prefs.hindi
        engine.setLanguage(if (hindi) Locale("hi", "IN") else Locale("en", "IN"))
        engine.setSpeechRate(prefs.speechRate)
        engine.speak(buildMessage(ride, hindi), TextToSpeech.QUEUE_FLUSH, null, "ride-${ride.key.hashCode()}")
        speaking = true
    }

    private fun buildMessage(ride: Ride, hindi: Boolean): String {
        val blockedPickup = Prefs.parseList(prefs.blockedPickup)
            .firstOrNull { ride.pickupAddr.contains(it, ignoreCase = true) }
        val blockedDrop = Prefs.parseList(prefs.blockedDrop)
            .firstOrNull { ride.dropAddr.contains(it, ignoreCase = true) }

        // सबसे पहले एरिया की जाँच, बाकी जानकारी नहीं पढ़ी जाएगी
        if (blockedPickup != null) {
            return if (hindi) "यह एरिया आपके लिए सही नहीं है. पिकअप एरिया $blockedPickup है."
            else "This area is not right for you. Pickup area is $blockedPickup."
        }
        if (blockedDrop != null) {
            return if (hindi) "यह एरिया आपके लिए सही नहीं है. ड्रॉप एरिया $blockedDrop है."
            else "This area is not right for you. Drop area is $blockedDrop."
        }

        val maxKm = prefs.maxPickupKm
        val far = maxKm > 0f && ride.pickupKm > maxKm
        val pk = formatKm(ride.pickupKm)
        val dk = formatKm(ride.dropKm)

        return if (hindi) {
            val warn = if (far) "पिकअप दूर है. " else ""
            "$warn${ride.pickupName}, $pk किलोमीटर. ड्रॉप ${ride.dropName}, $dk किलोमीटर."
        } else {
            val warn = if (far) "Pickup is far. " else ""
            "$warn${ride.pickupName}, $pk kilometers. Drop ${ride.dropName}, $dk kilometers."
        }
    }

    private fun formatKm(v: Double): String =
        if (v == Math.floor(v)) v.toInt().toString() else v.toString()

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val TICK_MS = 1000L
        private const val STALE_MS = 3000L
        private const val FORGET_MS = 4000L
    }
}
