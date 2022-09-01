package io.quarkus.mongodb.panache.binder;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

final class CommonQueryBinder {

    private static final String ISO_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    // the default DateTimeFormatter.ISO_LOCAL_DATE_TIME format with too many nano fraction (up to 9) for MongoDB (only 3)
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ofPattern(ISO_DATE_PATTERN);

    private CommonQueryBinder() {
    }

    static String replace(String query, String oldChars, Object value) {
        return query.replace(oldChars, escape(value));
    }

    static String escape(Object value) {
        if (Number.class.isAssignableFrom(value.getClass()) || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Date) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(ISO_DATE_PATTERN);
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date dateValue = (Date) value;
            return "ISODate('" + dateFormat.format(dateValue) + "')";
        }
        if (value instanceof LocalDate) {
            LocalDate dateValue = (LocalDate) value;
            return "ISODate('" + DateTimeFormatter.ISO_LOCAL_DATE.format(dateValue) + "')";
        }
        if (value instanceof LocalDateTime) {
            LocalDateTime dateValue = (LocalDateTime) value;
            return "ISODate('" + ISO_DATE_FORMATTER.format(dateValue.atZone(ZoneOffset.UTC)) + "')";
        }
        if (value instanceof Instant) {
            Instant dateValue = (Instant) value;
            return "ISODate('" + ISO_DATE_FORMATTER.format(dateValue.atZone(ZoneOffset.UTC)) + "')";
        }
        return "'" + value.toString().replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
