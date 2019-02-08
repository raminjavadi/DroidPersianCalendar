package com.byagowi.persiancalendar.adapter;

import android.os.Bundle;

import com.byagowi.persiancalendar.Constants;
import com.byagowi.persiancalendar.view.fragment.MonthFragment;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

public class CalendarAdapter extends FragmentStateAdapter {
    private CalendarAdapterHelper mCalendarAdapterHelper;

    public CalendarAdapter(FragmentManager fm, CalendarAdapterHelper calendarAdapterHelper) {
        super(fm);
        mCalendarAdapterHelper = calendarAdapterHelper;
    }

    @NonNull
    @Override
    public Fragment getItem(int position) {
        MonthFragment fragment = new MonthFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(Constants.OFFSET_ARGUMENT, mCalendarAdapterHelper.positionToOffset(position));
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public int getItemCount() {
        return mCalendarAdapterHelper.getMonthsLimit();
    }

    public static class CalendarAdapterHelper {
        private final int MONTHS_LIMIT = 5000; // this should be an even number

        public void gotoOffset(ViewPager2 monthViewPager, int offset, boolean isInitialization,
                               ViewPager2.OnPageChangeCallback changeListener) {
            if (monthViewPager.getCurrentItem() != positionToOffset(offset)) {
                monthViewPager.setCurrentItem(positionToOffset(offset), !isInitialization);
            }
            changeListener.onPageSelected(offset);
        }

        public int positionToOffset(int position) {
            return MONTHS_LIMIT / 2 - position;
        }

        int getMonthsLimit() {
            return MONTHS_LIMIT;
        }
    }
}
