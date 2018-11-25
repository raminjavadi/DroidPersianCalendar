package com.byagowi.persiancalendar.view.activity

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
import androidx.core.content.getSystemService
import androidx.databinding.DataBindingUtil
import com.byagowi.persiancalendar.Constants
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.databinding.ActivityAthanBinding
import com.byagowi.persiancalendar.util.UIUtils
import com.byagowi.persiancalendar.util.Utils
import java.io.IOException
import java.util.concurrent.TimeUnit

class AthanActivity : AppCompatActivity() {
    private lateinit var mRingtone: Ringtone
    private lateinit var mMediaPlayer: MediaPlayer
    private val mHandler = Handler()
    private var mStopTask: Runnable = object : Runnable {
        override fun run() {
            try {
                if (!mRingtone.isPlaying || !mMediaPlayer.isPlaying) {
                    this@AthanActivity.finish()
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
                this@AthanActivity.finish()
                return
            }

            mHandler.postDelayed(this, TimeUnit.SECONDS.toMillis(5))
        }
    }
    private var mPhoneStateListener: PhoneStateListener? = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String) {
            if (state == TelephonyManager.CALL_STATE_RINGING || state == TelephonyManager.CALL_STATE_OFFHOOK) {
                stop()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        getSystemService<AudioManager>()?.setStreamVolume(AudioManager.STREAM_ALARM, Utils.getAthanVolume(this), 0)

        val customAthanUri = Utils.getCustomAthanUri(this)
        if (customAthanUri != null) {
            mRingtone = RingtoneManager.getRingtone(this, customAthanUri).apply {
                try {
                    streamType = AudioManager.STREAM_ALARM
                    play()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            mMediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(this@AthanActivity, UIUtils.getDefaultAthanUri(this@AthanActivity))
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                    prepare()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                try {
                    start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

        }

        Utils.applyAppLanguage(this)

        val binding = DataBindingUtil.setContentView<ActivityAthanBinding>(this, R.layout.activity_athan)

        window?.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prayerKey = intent?.getStringExtra(Constants.KEY_EXTRA_PRAYER_KEY)
        if (prayerKey != null)
            binding.athanName.setText(UIUtils.getPrayTimeText(prayerKey))

        val root = binding.root
        root.setOnClickListener { stop() }
        if (prayerKey != null)
            root.setBackgroundResource(UIUtils.getPrayTimeImage(prayerKey))

        binding.place.text = String.format("%s %s",
                getString(R.string.in_city_time),
                Utils.getCityName(this, true))
        mHandler.postDelayed(mStopTask, TimeUnit.SECONDS.toMillis(10))

        try {
            getSystemService<TelephonyManager>()?.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        } catch (e: Exception) {
            Log.e(AthanActivity::class.java.name, "TelephonyManager handling fail", e)
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
            getSystemService<TelephonyManager>()?.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE)
            mPhoneStateListener = null
        } catch (e: RuntimeException) {
            Log.e(AthanActivity::class.java.name, "TelephonyManager handling fail", e)
        }

        try {
            mRingtone.stop()
        } catch (e: UninitializedPropertyAccessException) {
            e.printStackTrace()
        }


        try {
            if (mMediaPlayer.isPlaying) {
                mMediaPlayer.stop()
                mMediaPlayer.release()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }


        mHandler.removeCallbacks(mStopTask)
        finish()
    }
}
