package com.prolificinteractive.materialcalendarview.format;

import android.support.annotation.NonNull;

import com.prolificinteractive.materialcalendarview.CalendarDay;

import java.text.SimpleDateFormat;

/**
 * Supply labels for a given day. Default implementation is to format using a {@linkplain SimpleDateFormat}
 */
public interface DayFormatter {
    /**
     * @param day the day
     * @return a label for the day
     */
    @NonNull String format(@NonNull CalendarDay day);

    public static final DayFormatter DEFAULT = new DateFormatDayFormatter();
}
