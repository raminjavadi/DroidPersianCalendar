package com.byagowi.persiancalendar.view.fragment

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import com.byagowi.persiancalendar.Constants
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.databinding.DialogEmailBinding
import com.byagowi.persiancalendar.databinding.FragmentAboutBinding
import com.byagowi.persiancalendar.di.dependencies.MainActivityDependency
import com.byagowi.persiancalendar.util.Utils
import com.google.android.material.chip.Chip
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class AboutFragment : DaggerFragment() {

    @Inject
    lateinit var mMainActivityDependency: MainActivityDependency

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val binding = FragmentAboutBinding.inflate(inflater, container, false)

        val activity = mMainActivityDependency.mainActivity
        activity.setTitleAndSubtitle(getString(R.string.about), "")

        // version
        val version = programVersion(activity).split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        version[0] = Utils.formatNumber(version[0])
        binding.version.text = String.format(getString(R.string.version), TextUtils.join("\n", version))

        // licenses
        binding.licenses.setOnClickListener {
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(resources.getString(R.string.about_license_title))
            val licenseTextView = TextView(activity)
            licenseTextView.text = Utils.readRawResource(activity, R.raw.credits)
            licenseTextView.setPadding(20, 20, 20, 20)
            licenseTextView.typeface = Typeface.MONOSPACE
            Linkify.addLinks(licenseTextView, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
            val scrollView = ScrollView(activity)
            scrollView.addView(licenseTextView)
            builder.setView(scrollView)
            builder.setCancelable(true)
            builder.setNegativeButton(R.string.about_license_dialog_close, null)
            builder.show()
        }

        // help
        binding.aboutTitle.text = String.format(getString(R.string.about_help_subtitle),
                Utils.formatNumber(Utils.getMaxSupportedYear() - 1),
                Utils.formatNumber(Utils.getMaxSupportedYear()))
        when (Utils.getAppLanguage()) {
            Constants.LANG_FA, Constants.LANG_FA_AF, Constants.LANG_EN_IR // en. unlike en-US, is for Iranians as indicated also on UI
            -> {
            }
            else -> binding.helpCard.visibility = View.GONE
        }

        // report bug
        binding.reportBug.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                        "https://github.com/ebraminio/DroidPersianCalendar/issues/new".toUri()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        binding.email.setOnClickListener {
            val emailBinding = DialogEmailBinding.inflate(inflater, container, false)
            AlertDialog.Builder(mMainActivityDependency.mainActivity)
                    .setView(emailBinding.root)
                    .setTitle(R.string.about_email_sum)
                    .setPositiveButton(R.string.continue_button) { _, _ ->
                        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "ebrahim@gnu.org", null))
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                        try {
                            emailIntent.putExtra(Intent.EXTRA_TEXT,
                                    String.format(emailBinding.inputText.text.toString() + "\n\n\n\n\n\n\n===Device Information===\nManufacturer: %s\nModel: %s\nAndroid Version: %s\nApp Version Code: %s",
                                            Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, version[0]))
                            startActivity(Intent.createChooser(emailIntent, getString(R.string.about_sendMail)))
                        } catch (ex: android.content.ActivityNotFoundException) {
                            Toast.makeText(activity, getString(R.string.about_noClient), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(R.string.cancel, null).show()
        }

        val developerIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_developer)
        val designerIcon = AppCompatResources.getDrawable(activity, R.drawable.ic_designer)
        val color = TypedValue()
        activity.theme.resolveAttribute(R.attr.colorDrawerIcon, color, true)

        val layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutParams.setMargins(8, 8, 8, 8)

        val chipClick = { view: View ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/" + (view as Chip).text.toString()
                                .split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].split("\\)".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0])))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        for (line in getString(R.string.about_developers_list).trim { it <= ' ' }.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            binding.developers.addView(Chip(activity).apply {
                this.layoutParams = layoutParams
                setOnClickListener(chipClick)
                text = line
                chipIcon = developerIcon
                setChipIconTintResource(color.resourceId)
            })
        }

        for (line in getString(R.string.about_designers_list).trim { it <= ' ' }.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            binding.developers.addView(Chip(activity).apply {
                this.layoutParams = layoutParams
                setOnClickListener(chipClick)
                text = line
                chipIcon = designerIcon
                setChipIconTintResource(color.resourceId)
            })
        }

        for (line in getString(R.string.about_contributors_list).trim { it <= ' ' }.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            binding.developers.addView(Chip(activity).apply {
                this.layoutParams = layoutParams
                setOnClickListener(chipClick)
                text = line
                chipIcon = developerIcon
                setChipIconTintResource(color.resourceId)
            })
        }


        return binding.root
    }

    private fun programVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(AboutFragment::class.java.name, "Name not found on PersianCalendarUtils.programVersion")
            ""
        }

    }
}
