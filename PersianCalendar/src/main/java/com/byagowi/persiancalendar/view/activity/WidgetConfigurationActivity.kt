package com.byagowi.persiancalendar.view.activity

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.byagowi.persiancalendar.Constants.LIGHT_THEME
import com.byagowi.persiancalendar.Constants.PREF_THEME
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.databinding.WidgetPreferenceLayoutBinding
import com.byagowi.persiancalendar.util.UIUtils
import com.byagowi.persiancalendar.util.UpdateUtils
import com.byagowi.persiancalendar.util.Utils
import com.byagowi.persiancalendar.view.preferences.FragmentWidgetNotification

class WidgetConfigurationActivity : AppCompatActivity() {
    private fun finishAndSuccess() {
        val extras = intent?.extras
        val appwidgetId = extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID)
        setResult(Activity.RESULT_OK, Intent()
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appwidgetId))
        Utils.updateStoredPreference(this)
        UpdateUtils.update(this, false)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Don't replace below with appDependency.getSharedPreferences() ever
        // as the injection won't happen at the right time
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        setTheme(UIUtils.getThemeFromName(prefs.getString(PREF_THEME, LIGHT_THEME) ?: LIGHT_THEME))

        Utils.applyAppLanguage(this)
        super.onCreate(savedInstanceState)
        val binding = DataBindingUtil.setContentView<WidgetPreferenceLayoutBinding>(this, R.layout.widget_preference_layout)

        supportFragmentManager.beginTransaction().add(
                R.id.preference_fragment_holder,
                FragmentWidgetNotification(), "TAG").commit()

        binding.addWidgetButton.setOnClickListener { finishAndSuccess() }
    }
}
