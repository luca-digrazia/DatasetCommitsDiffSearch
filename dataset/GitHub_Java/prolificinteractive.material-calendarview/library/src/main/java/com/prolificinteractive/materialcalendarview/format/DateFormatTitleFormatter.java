package com.prolificinteractive.materialcalendarview.format;

import com.prolificinteractive.materialcalendarview.CalendarDay;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Format using a {@linkplain java.text.DateFormat} instance.
 */
public class DateFormatTitleFormatter implements TitleFormatter {

    private final DateFormat dateFormat;

    public DateFormatTitleFormatter() {
        this.dateFormat = new SimpleDateFormat(
                "MMMM yyyy", Locale.getDefault()
        );
    }

    public DateFormatTitleFormatter(DateFormat format) {
        this.dateFormat = format;
    }

    @Override
    public CharSequence format(CalendarDay day) {
        return dateFormat.format(day.getDate());
    }
}
