package com.adamkoski.calendarwidget;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.adamkoski.library.calendarwidget.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static java.util.Calendar.MONTH;

/**
 *
 */
public class CalendarWidget extends LinearLayout implements View.OnClickListener, MonthView.Callbacks, NumberPicker.OnValueChangeListener {

    private static final DateFormat TITLE_FORMAT = new SimpleDateFormat("MMMM yyyy");

    private final TextView title;
    private final DirectionButton buttonPast;
    private final DirectionButton buttonFuture;
    private final ViewSwitcher switcher;
    private final MonthView monthView;
    private final NumberPicker yearView;

    private final Calendar calendar = Calendar.getInstance();
    private final Calendar selectedDate = CalendarUtils.copy(Calendar.getInstance());
    private CalendarDay minDate = null;
    private CalendarDay maxDate = null;

    private OnDateChangedListener listener;

    public CalendarWidget(Context context) {
        this(context, null);
    }

    public CalendarWidget(Context context, AttributeSet attrs) {
        super(context, attrs);

        CalendarUtils.setToFirstDay(calendar);

        setOrientation(VERTICAL);

        LayoutInflater.from(getContext()).inflate(R.layout.cw__calendar_widget, this);

        title = (TextView) findViewById(R.id.___calendar_widget_title);
        buttonPast = (DirectionButton) findViewById(R.id.___calendar_widget_button_backwards);
        buttonFuture = (DirectionButton) findViewById(R.id.___calendar_widget_button_forward);
        switcher = (ViewSwitcher) findViewById(R.id.___calendar_widget_switcher);
        monthView = (MonthView) findViewById(R.id.___calendar_widget_month);
        yearView = (NumberPicker) findViewById(R.id.___calendar_widget_year);

        yearView.setOnValueChangedListener(this);

        title.setOnClickListener(this);
        buttonPast.setOnClickListener(this);
        buttonFuture.setOnClickListener(this);

        monthView.setCallbacks(this);

        updateUi();
    }

    private void updateUi() {
        title.setText(TITLE_FORMAT.format(calendar.getTime()));
        monthView.setMinimumDate(minDate);
        monthView.setMaximumDate(maxDate);
        monthView.setSelectedDate(selectedDate);
        monthView.setDate(calendar);
        buttonPast.setEnabled(canGoBack());
        buttonFuture.setEnabled(canGoForward());

        yearView.setMinValue(minDate == null ? 0 : minDate.getYear());
        yearView.setMaxValue(maxDate == null ? 0 : maxDate.getYear());
        yearView.setValue(calendar.get(Calendar.YEAR));

        setColor(getAccentColor());
    }

    private boolean canGoForward() {
        if(maxDate == null) {
            return true;
        }

        Calendar maxCal = maxDate.getCalendar();
        maxCal.add(MONTH, -1);
        return maxCal.compareTo(calendar) >= 0;
    }

    private boolean canGoBack() {
        if(minDate == null) {
            return true;
        }

        Calendar minCal = minDate.getCalendar();
        return minCal.compareTo(calendar) < 0;
    }

    private int getAccentColor() {
        int colorAttr = 0;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            colorAttr = android.R.attr.colorAccent;
        } else {
            colorAttr = getResources().getIdentifier("colorAccent", "attr", getContext().getPackageName());
        }
        TypedValue outValue = new TypedValue();
        getContext().getTheme().resolveAttribute(colorAttr, outValue, true);
        return outValue.data;
    }

    public void setColor(int color) {
        buttonPast.setColor(color);
        buttonFuture.setColor(color);
        monthView.setColor(color);
    }

    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.___calendar_widget_button_forward) {
            calendar.add(MONTH, 1);
            updateUi();
        } else if(v.getId() == R.id.___calendar_widget_button_backwards) {
            calendar.add(MONTH, -1);
            updateUi();
        } else if(v.getId() == R.id.___calendar_widget_title) {
            switcher.showNext();
        }
    }

    public void setOnDateChangedListener(OnDateChangedListener listener) {
        this.listener = listener;
    }

    public CalendarDay getSelectedDate() {
        return new CalendarDay(selectedDate);
    }

    public void setSelectedDate(Calendar calendar) {
        setSelectedDate(new CalendarDay(calendar));
    }

    public void setSelectedDate(Date date) {
        setSelectedDate(new CalendarDay(date));
    }

    public void setSelectedDate(CalendarDay day) {
        day.copyTo(selectedDate);
        day.copyTo(calendar);
        CalendarUtils.setToFirstDay(calendar);
        updateUi();
    }

    public void setMinimumDate(Calendar calendar) {
        minDate = calendar == null ? null : new CalendarDay(calendar);
        updateUi();
    }

    public void setMaximumDate(Calendar calendar) {
        maxDate = calendar == null ? null : new CalendarDay(calendar);
        updateUi();
    }

    @Override
    public void onDateChanged(CalendarDay date) {
        setSelectedDate(date);

        if(listener != null) {
            listener.onDateChanged(this, date);
        }
    }

    @Override
    public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        calendar.set(Calendar.YEAR, newVal);
        clampCalendar();
        updateUi();
    }

    private void clampCalendar() {
        if(maxDate != null && maxDate.getCalendar().compareTo(calendar) < 0) {
            maxDate.copyTo(calendar);
        }
        if(minDate != null && minDate.getCalendar().compareTo(calendar) > 0) {
            minDate.copyTo(calendar);
        }
        CalendarUtils.setToFirstDay(calendar);
    }
}
