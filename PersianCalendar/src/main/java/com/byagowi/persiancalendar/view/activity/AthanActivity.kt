package com.byagowi.persiancalendar.view.activity

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.byagowi.persiancalendar.Constants
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.databinding.ActivityAthanBinding
import com.byagowi.persiancalendar.util.UIUtils
import com.byagowi.persiancalendar.util.Utils
import java.io.IOException
import java.util.concurrent.TimeUnit

class AthanActivity : AppCompatActivity() {
    private lateinit var ringtone: Ringtone
    private lateinit var mediaPlayer: MediaPlayer
    private val handler = Handler()
    var stopTask: Runnable = object : Runnable {
        override fun run() {
            try {
                if (!ringtone.isPlaying) {
                    this@AthanActivity.finish()
                    return
                }
                if (!mediaPlayer.isPlaying) {
                    this@AthanActivity.finish()
                    return
                }

            } catch (e: Exception) {
                e.printStackTrace()
                this@AthanActivity.finish()
                return
            }

            handler.postDelayed(this, TimeUnit.SECONDS.toMillis(5))
        }
    }
    private var phoneStateListener: PhoneStateListener? = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String) {
            if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                stop()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, Utils.getAthanVolume(this), 0)

        val customAthanUri = Utils.getCustomAthanUri(this)
        if (customAthanUri != null) {
            try {
                ringtone = RingtoneManager.getRingtone(this, customAthanUri)
                ringtone.streamType = AudioManager.STREAM_ALARM
                ringtone.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        } else {
            val player = MediaPlayer()
            try {
                player.setDataSource(this, UIUtils.getDefaultAthanUri(this))
                player.setAudioStreamType(AudioManager.STREAM_ALARM)
                player.prepare()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            try {
                player.start()
                mediaPlayer = player
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

        Utils.applyAppLanguage(this)

        val binding = DataBindingUtil.setContentView<ActivityAthanBinding>(this, R.layout.activity_athan)

        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prayerKey = intent.getStringExtra(Constants.KEY_EXTRA_PRAYER_KEY)
        binding.athanName.setText(UIUtils.getPrayTimeText(prayerKey))

        val root = binding.root
        root.setOnClickListener { v -> stop() }
        root.setBackgroundResource(UIUtils.getPrayTimeImage(prayerKey))

        binding.place.text = String.format("%s %s",
                getString(R.string.in_city_time),
                Utils.getCityName(this, true))
        handler.postDelayed(stopTask, TimeUnit.SECONDS.toMillis(10))

        try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Exception) {
            Log.e(TAG, "TelephonyManager handling fail", e)
        }

    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            stop()
        }
    }

    override fun onBackPressed() {
        stop()
    }

    private fun stop() {
        try {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
            phoneStateListener = null
        } catch (e: RuntimeException) {
            Log.e(TAG, "TelephonyManager handling fail", e)
        }


        ringtone.stop()

        try {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
                mediaPlayer.release()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }


        handler.removeCallbacks(stopTask)
        finish()
    }

    companion object {
        private val TAG = AthanActivity::class.java.name
    }
}
