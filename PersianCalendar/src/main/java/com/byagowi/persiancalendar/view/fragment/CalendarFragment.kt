package com.byagowi.persiancalendar.view.fragment

import android.Manifest
import android.animation.LayoutTransition
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.*
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.viewpager.widget.ViewPager
import com.byagowi.persiancalendar.Constants
import com.byagowi.persiancalendar.Constants.CALENDAR_EVENT_ADD_MODIFY_REQUEST_CODE
import com.byagowi.persiancalendar.Constants.PREF_HOLIDAY_TYPES
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.adapter.CalendarAdapter
import com.byagowi.persiancalendar.adapter.CardTabsAdapter
import com.byagowi.persiancalendar.adapter.TimeItemAdapter
import com.byagowi.persiancalendar.calendar.CivilDate
import com.byagowi.persiancalendar.databinding.EventsTabContentBinding
import com.byagowi.persiancalendar.databinding.FragmentCalendarBinding
import com.byagowi.persiancalendar.databinding.OwghatTabContentBinding
import com.byagowi.persiancalendar.di.dependencies.AppDependency
import com.byagowi.persiancalendar.di.dependencies.MainActivityDependency
import com.byagowi.persiancalendar.entity.AbstractEvent
import com.byagowi.persiancalendar.entity.DeviceCalendarEvent
import com.byagowi.persiancalendar.praytimes.Coordinate
import com.byagowi.persiancalendar.praytimes.PrayTimesCalculator
import com.byagowi.persiancalendar.util.CalendarUtils
import com.byagowi.persiancalendar.util.UIUtils
import com.byagowi.persiancalendar.util.Utils
import com.byagowi.persiancalendar.view.CalendarsView
import com.byagowi.persiancalendar.view.dialog.SelectDayDialog
import com.cepmuvakkit.times.posAlgo.SunMoonPosition
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import dagger.android.support.DaggerFragment
import java.util.*
import javax.inject.Inject

class CalendarFragment : DaggerFragment() {
    @Inject
    lateinit var mAppDependency: AppDependency // same object from App
    @Inject
    lateinit var mMainActivityDependency: MainActivityDependency // same object from MainActivity
    var mFirstTime = true
    private val mCalendar = Calendar.getInstance()
    private lateinit var mCoordinate: Coordinate
    var viewPagerPosition: Int = 0
        private set
    private lateinit var mMainBinding: FragmentCalendarBinding
    private lateinit var mCalendarsView: CalendarsView
    private lateinit var mOwghatBinding: OwghatTabContentBinding
    private lateinit var mEventsBinding: EventsTabContentBinding
    private var mLastSelectedJdn: Long = -1
    private lateinit var mSearchView: SearchView
    private lateinit var mSearchAutoComplete: SearchView.SearchAutoComplete
    private lateinit var mCalendarAdapterHelper: CalendarAdapter.CalendarAdapterHelper
    private val mChangeListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            sendBroadcastToMonthFragments(mCalendarAdapterHelper.positionToOffset(position), false)
            mMainBinding.todayButton.show()
        }

    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val context = mMainActivityDependency.mainActivity

        setHasOptionsMenu(true)

        mMainBinding = FragmentCalendarBinding.inflate(inflater, container, false)
        viewPagerPosition = 0

        val titles = ArrayList<String>()
        val tabs = ArrayList<View>()

        titles.add(getString(R.string.calendar))
        mCalendarsView = CalendarsView(context)
        mCalendarsView.setOnCalendarsViewExpandListener { mMainBinding.cardsViewPager.measureCurrentView(mCalendarsView) }
        mCalendarsView.setOnShowHideTodayButton { show ->
            if (show)
                mMainBinding.todayButton.show()
            else
                mMainBinding.todayButton.hide()
        }
        mMainBinding.todayButton.setOnClickListener { bringTodayYearMonth() }
        tabs.add(mCalendarsView)

        titles.add(getString(R.string.events))
        mEventsBinding = EventsTabContentBinding.inflate(inflater, container, false)
        tabs.add(mEventsBinding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            val layoutTransition = LayoutTransition()
            layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
            mEventsBinding.eventsContent.layoutTransition = layoutTransition
            // Don't do the same for others tabs, it is problematic
        }

        mCoordinate = Utils.getCoordinate(context)!!
        if (!this::mCoordinate.isInitialized) {
            titles.add(getString(R.string.owghat))
            mOwghatBinding = OwghatTabContentBinding.inflate(inflater, container, false)
            tabs.add(mOwghatBinding.root)
            mOwghatBinding.root.setOnClickListener { this.onOwghatClick(it) }
            mOwghatBinding.cityName.setOnClickListener { this.onOwghatClick(it) }
            // Easter egg to test AthanActivity
            mOwghatBinding.cityName.setOnLongClickListener {
                Utils.startAthan(context, "FAJR")
                true
            }
            val cityName = Utils.getCityName(context, false)
            if (!TextUtils.isEmpty(cityName)) {
                mOwghatBinding.cityName.text = cityName
            }

            val layoutManager = FlexboxLayoutManager(context)
            layoutManager.flexWrap = FlexWrap.WRAP
            layoutManager.justifyContent = JustifyContent.CENTER
            mOwghatBinding.timesRecyclerView.layoutManager = layoutManager
            mOwghatBinding.timesRecyclerView.adapter = TimeItemAdapter()
        }

        mMainBinding.cardsViewPager.adapter = CardTabsAdapter(childFragmentManager,
                mAppDependency, tabs, titles)
        mMainBinding.tabLayout.setupWithViewPager(mMainBinding.cardsViewPager)

        mCalendarAdapterHelper = CalendarAdapter.CalendarAdapterHelper(UIUtils.isRTL(context))
        mMainBinding.calendarViewPager.adapter = CalendarAdapter(childFragmentManager,
                mCalendarAdapterHelper)
        mCalendarAdapterHelper.gotoOffset(mMainBinding.calendarViewPager, 0)

        mMainBinding.calendarViewPager.addOnPageChangeListener(mChangeListener)

        var lastTab = mAppDependency.sharedPreferences
                .getInt(Constants.LAST_CHOSEN_TAB_KEY, Constants.CALENDARS_TAB)
        if (lastTab >= tabs.size) {
            lastTab = Constants.CALENDARS_TAB
        }

        mMainBinding.cardsViewPager.setCurrentItem(lastTab, false)

        val today = CalendarUtils.getTodayOfCalendar(Utils.getMainCalendar())
        mMainActivityDependency.mainActivity.setTitleAndSubtitle(CalendarUtils.getMonthName(today),
                Utils.formatNumber(today.year))

        return mMainBinding.root
    }

    fun changeMonth(position: Int) {
        mMainBinding.calendarViewPager.setCurrentItem(
                mMainBinding.calendarViewPager.currentItem + position, true)
    }

    fun selectDay(jdn: Long) {
        mLastSelectedJdn = jdn
        mCalendarsView.showCalendars(jdn, Utils.getMainCalendar(), Utils.getEnabledCalendarTypes())
        setOwghat(jdn, CalendarUtils.getTodayJdn() == jdn)
        showEvent(jdn)
    }

    fun addEventOnCalendar(jdn: Long) {
        val activity = mMainActivityDependency.mainActivity

        val civil = CivilDate(jdn)
        val time = Calendar.getInstance()
        time.set(civil.year, civil.month - 1, civil.dayOfMonth)
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            UIUtils.askForCalendarPermission(activity)
        } else {
            try {
                startActivityForResult(
                        Intent(Intent.ACTION_INSERT)
                                .setData(CalendarContract.Events.CONTENT_URI)
                                .putExtra(CalendarContract.Events.DESCRIPTION, CalendarUtils.dayTitleSummary(
                                        CalendarUtils.getDateFromJdnOfCalendar(Utils.getMainCalendar(), jdn)))
                                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                                        time.timeInMillis)
                                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,
                                        time.timeInMillis)
                                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true),
                        CALENDAR_EVENT_ADD_MODIFY_REQUEST_CODE)
            } catch (e: Exception) {
                Toast.makeText(activity, R.string.device_calendar_does_not_support, Toast.LENGTH_SHORT).show()
            }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val activity = mMainActivityDependency.mainActivity

        if (requestCode == CALENDAR_EVENT_ADD_MODIFY_REQUEST_CODE) {
            if (Utils.isShowDeviceCalendarEvents()) {
                sendBroadcastToMonthFragments(calculateViewPagerPositionFromJdn(mLastSelectedJdn), true)
            } else {
                if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    UIUtils.askForCalendarPermission(activity)
                } else {
                    UIUtils.toggleShowDeviceCalendarOnPreference(activity, true)
                    activity.recreate()
                }
            }
        }
    }

    private fun sendBroadcastToMonthFragments(toWhich: Int, addOrModify: Boolean) {
        mAppDependency.localBroadcastManager.sendBroadcast(
                Intent(Constants.BROADCAST_INTENT_TO_MONTH_FRAGMENT)
                        .putExtra(Constants.BROADCAST_FIELD_TO_MONTH_FRAGMENT, toWhich)
                        .putExtra(Constants.BROADCAST_FIELD_EVENT_ADD_MODIFY, addOrModify)
                        .putExtra(Constants.BROADCAST_FIELD_SELECT_DAY_JDN, mLastSelectedJdn))
    }

    private fun formatClickableEventTitle(event: DeviceCalendarEvent): SpannableString {
        val title = UIUtils.formatDeviceCalendarEventTitle(event)
        val ss = SpannableString(title)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(textView: View) {
                try {
                    startActivityForResult(Intent(Intent.ACTION_VIEW)
                            .setData(ContentUris.withAppendedId(
                                    CalendarContract.Events.CONTENT_URI, event.id.toLong())),
                            CALENDAR_EVENT_ADD_MODIFY_REQUEST_CODE)
                } catch (e: Exception) { // Should be ActivityNotFoundException but we don't care really
                    Toast.makeText(mMainActivityDependency.mainActivity,
                            R.string.device_calendar_does_not_support, Toast.LENGTH_SHORT).show()
                }

            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                val color = event.color
                if (!TextUtils.isEmpty(color)) {
                    try {
                        ds.color = Integer.parseInt(color)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                }
            }
        }
        ss.setSpan(clickableSpan, 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return ss
    }

    private fun getDeviceEventsTitle(dayEvents: List<AbstractEvent<*>>): SpannableStringBuilder {
        val titles = SpannableStringBuilder()
        var first = true

        for (event in dayEvents)
            if (event is DeviceCalendarEvent) {
                if (first)
                    first = false
                else
                    titles.append("\n")

                titles.append(formatClickableEventTitle(event))
            }

        return titles
    }

    private fun showEvent(jdn: Long) {
        val events = Utils.getEvents(jdn,
                CalendarUtils.readDayDeviceEvents(mMainActivityDependency.mainActivity, jdn))
        val holidays = Utils.getEventsTitle(events, true, false, false, false)
        val nonHolidays = Utils.getEventsTitle(events, false, false, false, false)
        val deviceEvents = getDeviceEventsTitle(events)
        val contentDescription = StringBuilder()

        mEventsBinding.eventMessage.visibility = View.GONE
        mEventsBinding.noEvent.visibility = View.VISIBLE

        if (!TextUtils.isEmpty(holidays)) {
            mEventsBinding.noEvent.visibility = View.GONE
            mEventsBinding.holidayTitle.text = holidays
            val holidayContent = getString(R.string.holiday_reason) + "\n" + holidays
            mEventsBinding.holidayTitle.contentDescription = holidayContent
            contentDescription.append(holidayContent)
            mEventsBinding.holidayTitle.visibility = View.VISIBLE
        } else {
            mEventsBinding.holidayTitle.visibility = View.GONE
        }

        if (deviceEvents.isNotEmpty()) {
            mEventsBinding.noEvent.visibility = View.GONE
            mEventsBinding.deviceEventTitle.text = deviceEvents
            contentDescription.append("\n")
            contentDescription.append(getString(R.string.show_device_calendar_events))
            contentDescription.append("\n")
            contentDescription.append(deviceEvents)
            mEventsBinding.deviceEventTitle.movementMethod = LinkMovementMethod.getInstance()

            mEventsBinding.deviceEventTitle.visibility = View.VISIBLE
        } else {
            mEventsBinding.deviceEventTitle.visibility = View.GONE
        }


        if (!TextUtils.isEmpty(nonHolidays)) {
            mEventsBinding.noEvent.visibility = View.GONE
            mEventsBinding.eventTitle.text = nonHolidays
            contentDescription.append("\n")
            contentDescription.append(getString(R.string.events))
            contentDescription.append("\n")
            contentDescription.append(nonHolidays)

            mEventsBinding.eventTitle.visibility = View.VISIBLE
        } else {
            mEventsBinding.eventTitle.visibility = View.GONE
        }

        val messageToShow = SpannableStringBuilder()

        val enabledTypes = mAppDependency.sharedPreferences
                .getStringSet(PREF_HOLIDAY_TYPES, HashSet())
        if (enabledTypes == null || enabledTypes.size == 0) {
            mEventsBinding.noEvent.visibility = View.GONE
            if (!TextUtils.isEmpty(messageToShow))
                messageToShow.append("\n")

            val title = getString(R.string.warn_if_events_not_set)
            val ss = SpannableString(title)
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(textView: View) {
                    mMainActivityDependency.mainActivity.navigateTo(R.id.settings)
                }
            }
            ss.setSpan(clickableSpan, 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            messageToShow.append(ss)

            contentDescription.append("\n")
            contentDescription.append(title)
        }

        if (!TextUtils.isEmpty(messageToShow)) {
            mEventsBinding.eventMessage.text = messageToShow
            mEventsBinding.eventMessage.movementMethod = LinkMovementMethod.getInstance()

            mEventsBinding.eventMessage.visibility = View.VISIBLE
        }

        mEventsBinding.root.contentDescription = contentDescription
    }

    private fun setOwghat(jdn: Long, isToday: Boolean) {

        val civilDate = CivilDate(jdn)
        mCalendar.set(civilDate.year, civilDate.month - 1, civilDate.dayOfMonth)
        val date = mCalendar.time

        val prayTimes = PrayTimesCalculator.calculate(Utils.getCalculationMethod(),
                date, mCoordinate)
        val adapter = mOwghatBinding.timesRecyclerView.adapter
        if (adapter is TimeItemAdapter) {
            adapter.setTimes(prayTimes)
        }

        var moonPhase = 1.0

        try {
            moonPhase = SunMoonPosition(CalendarUtils.getTodayJdn().toDouble(), mCoordinate.latitude,
                    mCoordinate.longitude, 0.0, 0.0).moonPhase
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mOwghatBinding.sunView.setSunriseSunsetMoonPhase(prayTimes, moonPhase)
        if (isToday) {
            mOwghatBinding.sunView.visibility = View.VISIBLE
            if (mMainBinding.cardsViewPager.currentItem == Constants.OWGHAT_TAB) {
                mOwghatBinding.sunView.startAnimate(true)
            }
        } else {
            mOwghatBinding.sunView.visibility = View.GONE
        }
    }

    private fun onOwghatClick(v: View) {
        val adapter = mOwghatBinding.timesRecyclerView.adapter
        if (adapter is TimeItemAdapter) {
            val expanded = !adapter.isExpanded
            adapter.isExpanded = expanded
            mOwghatBinding.moreOwghat.setImageResource(if (expanded)
                R.drawable.ic_keyboard_arrow_up
            else
                R.drawable.ic_keyboard_arrow_down)
        }
        mMainBinding.cardsViewPager.measureCurrentView(mOwghatBinding.root)

        if (mLastSelectedJdn.equals(-1))
            mLastSelectedJdn = CalendarUtils.getTodayJdn()
    }

    private fun bringTodayYearMonth() {
        mLastSelectedJdn = -1
        sendBroadcastToMonthFragments(Constants.BROADCAST_TO_MONTH_FRAGMENT_RESET_DAY, false)

        mCalendarAdapterHelper.gotoOffset(mMainBinding.calendarViewPager, 0)

        selectDay(CalendarUtils.getTodayJdn())
    }

    fun bringDate(jdn: Long) {
        val context = context ?: return

        viewPagerPosition = calculateViewPagerPositionFromJdn(jdn)
        mCalendarAdapterHelper.gotoOffset(mMainBinding.calendarViewPager, viewPagerPosition)

        selectDay(jdn)
        sendBroadcastToMonthFragments(viewPagerPosition, false)

        if (Utils.isTalkBackEnabled()) {
            val todayJdn = CalendarUtils.getTodayJdn()
            if (jdn != todayJdn) {
                Toast.makeText(context, CalendarUtils.getA11yDaySummary(context, jdn,
                        false, null, true,
                        true, true), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun calculateViewPagerPositionFromJdn(jdn: Long): Int {
        val mainCalendar = Utils.getMainCalendar()
        val today = CalendarUtils.getTodayOfCalendar(mainCalendar)
        val date = CalendarUtils.getDateFromJdnOfCalendar(mainCalendar, jdn)
        return (today.year - date.year) * 12 + today.month - date.month
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        inflater.inflate(R.menu.calendar_menu_buttons, menu)

        mSearchView = menu.findItem(R.id.search).actionView as SearchView
        mSearchView.setOnSearchClickListener {
            try {
                mSearchAutoComplete.onItemClickListener = null
            } catch (e: UninitializedPropertyAccessException) {
                e.printStackTrace()
            }

            val context = context ?: return@setOnSearchClickListener

            mSearchAutoComplete = mSearchView.findViewById(androidx.appcompat.R.id.search_src_text)
            mSearchAutoComplete.setHint(R.string.search_in_events)

            val eventsAdapter = ArrayAdapter<AbstractEvent<*>>(context,
                    R.layout.suggestion, android.R.id.text1)
            eventsAdapter.addAll(Utils.getAllEnabledEvents())
            eventsAdapter.addAll(CalendarUtils.getAllEnabledAppointments(context))
            mSearchAutoComplete.setAdapter(eventsAdapter)
            mSearchAutoComplete.setOnItemClickListener { parent, _, position, _ ->
                val ev = parent.getItemAtPosition(position) as AbstractEvent<*>
                val date = ev.date
                val type = CalendarUtils.getCalendarTypeFromDate(date)
                val today = CalendarUtils.getTodayOfCalendar(type)
                var year = date.year
                if (year == -1) {
                    year = today.year + if (date.month < today.month) 1 else 0
                }
                bringDate(CalendarUtils.getDateOfCalendar(type, year, date.month, date.dayOfMonth).toJdn())
                mSearchView.onActionViewCollapsed()
            }
        }
    }

    private fun destroySearchView() {
        try {
            mSearchView.setOnSearchClickListener(null)
            mSearchView.visibility = View.GONE
        } catch (e: UninitializedPropertyAccessException) {
            e.printStackTrace()
        }

        try {
            mSearchAutoComplete.setAdapter(null)
            mSearchAutoComplete.onItemClickListener = null
            mSearchAutoComplete.visibility = View.GONE
        } catch (e: UninitializedPropertyAccessException) {
            e.printStackTrace()
        }

    }

    override fun onDestroyOptionsMenu() {
        destroySearchView()
        super.onDestroyOptionsMenu()
    }

    override fun onDestroy() {
        destroySearchView()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.go_to -> SelectDayDialog.newInstance(mLastSelectedJdn).show(childFragmentManager,
                    SelectDayDialog::class.java.name)
            R.id.add_event -> {
                if (mLastSelectedJdn.equals(-1))
                    mLastSelectedJdn = CalendarUtils.getTodayJdn()

                addEventOnCalendar(mLastSelectedJdn)
            }
            else -> {
            }
        }
        return true
    }

    fun closeSearch(): Boolean {
        if (!mSearchView.isIconified) {
            mSearchView.onActionViewCollapsed()
            return true
        }
        return false
    }

}
