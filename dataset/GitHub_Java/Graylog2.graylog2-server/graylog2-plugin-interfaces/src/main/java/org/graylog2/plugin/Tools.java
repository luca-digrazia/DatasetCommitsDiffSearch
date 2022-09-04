/**
 * Copyright 2010 Lennart Koopmann <lennart@socketfeed.com>
 * 
 * This file is part of Graylog2.
 *
 * Graylog2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog2.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.graylog2.plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import com.google.common.primitives.Ints;
import org.drools.util.codec.Base64;
import org.elasticsearch.search.SearchHit;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.*;

/**
 * Utilty class for various tool/helper functions.
 *
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public final class Tools {

    public static final String ES_DATE_FORMAT = "yyyy-MM-dd HH-mm-ss.SSS";
    public static final String ES_DATE_FORMAT_NO_MS = "yyyy-MM-dd HH-mm-ss";

    private Tools() { }

    /**
     * Get the own PID of this process.
     *
     * @return PID of the running process
     */
    public static String getPID() {
        return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
    }

    /**
     * Converts integer syslog loglevel to human readable string
     *
     * @param level The level to convert
     * @return The human readable level
     */
    public static String syslogLevelToReadable(int level) {
        switch (level) {
            case 0:
                return "Emergency";
            case 1:
                return "Alert";
            case 2:
                return"Critical";
            case 3:
                return "Error";
            case 4:
                return "Warning";
            case 5:
                return "Notice";
            case 6:
                return "Informational";
            case 7:
                return "Debug";
        }

        return "Invalid";
    }

    /**
     * Converts integer syslog facility to human readable string
     *
     * @param facility The facility to convert
     * @return The human readable facility
     */
    public static String syslogFacilityToReadable(int facility) {
        switch (facility) {
            case 0:  return "kernel";
            case 1:  return "user-level";
            case 2:  return "mail";
            case 3:  return "system daemon";
            case 4: case 10: return "security/authorization";
            case 5:  return "syslogd";
            case 6:  return "line printer";
            case 7:  return "network news";
            case 8:  return "UUCP";
            case 9: case 15: return "clock";
            case 11: return "FTP";
            case 12: return "NTP";
            case 13: return "log audit";
            case 14: return "log alert";

            // TODO: Make user definable?
            case 16: return "local0";
            case 17: return "local1";
            case 18: return "local2";
            case 19: return "local3";
            case 20: return "local4";
            case 21: return "local5";
            case 22: return "local6";
            case 23: return "local7";
        }

        return "Unknown";
    }

    /**
     * Get a String containing version information of JRE, OS, ...
     * @return Descriptive string of JRE and OS
     */
    public static String getSystemInformation() {
        String ret = System.getProperty("java.vendor");
        ret += " " + System.getProperty("java.version");
        ret += " on " + System.getProperty("os.name");
        ret += " " + System.getProperty("os.version");
        return ret;
    }


    /**
     * Decompress ZLIB (RFC 1950) compressed data
     *
     * @return A string containing the decompressed data
     */
    public static String decompressZlib(byte[] compressedData) throws IOException {
        byte[] buffer = new byte[compressedData.length];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(compressedData));
        for (int bytesRead = 0; bytesRead != -1; bytesRead = in.read(buffer)) {
            out.write(buffer, 0, bytesRead);
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    /**
     * Decompress GZIP (RFC 1952) compressed data
     * 
     * @return A string containing the decompressed data
     */
    public static String decompressGzip(byte[] compressedData) throws IOException {
        byte[] buffer = new byte[compressedData.length];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(compressedData));
        for (int bytesRead = 0; bytesRead != -1; bytesRead = in.read(buffer)) {
            out.write(buffer, 0, bytesRead);
        }
        return new String(out.toByteArray(), "UTF-8");
    }

    /**
     *
     * @return The current UTC UNIX timestamp.
     */
    public static int getUTCTimestamp() {
       return (int) (System.currentTimeMillis()/1000);
    }

    /**
     * Get the current UNIX epoch with milliseconds of the system
     *
     * @return The current UTC UNIX timestamp with milliseconds.
     */
    public static double getUTCTimestampWithMilliseconds() {
        return getUTCTimestampWithMilliseconds(System.currentTimeMillis());
    }

    /**
     * Get the UNIX epoch with milliseconds of the provided millisecond timestamp
     *
     * @param timestamp a millisecond timestamp (milliseconds since UNIX epoch)
     * @return The current UTC UNIX timestamp with milliseconds.
     */
    public static double getUTCTimestampWithMilliseconds(long timestamp) {
        return timestamp / 1000.0;
    }

    public static String getLocalHostname() {
        InetAddress addr = null;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException ex) {
            return "Unknown";
        }

        return addr.getHostName();
    }
    
    public static String getLocalCanonicalHostname() {
        InetAddress addr = null;
        try {
            addr = InetAddress.getLocalHost();
        } catch (UnknownHostException ex) {
            return "Unknown";
        }

        return addr.getCanonicalHostName();
    }

    public static int getTimestampDaysAgo(int ts, int days) {
        return (ts - (days*86400));
    }

    public static String encodeBase64(String what) {
        return new String(Base64.encodeBase64(what.getBytes()));
    }

    public static String decodeBase64(String what) {
        return new String(Base64.decodeBase64(what));
    }

    public static String rdnsLookup(InetAddress socketAddress) throws UnknownHostException {
        return socketAddress.getCanonicalHostName();
    }
    
    public static String generateServerId() {
        UUID id = UUID.randomUUID();

        return getLocalHostname() + "-" + id.toString();
    }
    
    public static <T extends Comparable<? super T>> List<T> asSortedList(Collection<T> c) {
        List<T> list = new ArrayList<T>(c);
        java.util.Collections.sort(list);
        return list;
    }

    public static String buildElasticSearchTimeFormat(Object timestamp) {
        if (timestamp instanceof Double || timestamp instanceof Integer) {
            return buildElasticSearchTimeFormatFromDouble((Double) timestamp);
        }

        if (timestamp instanceof DateTime) {
            return buildElasticSearchTimeFormatFromDateTime((DateTime) timestamp);
        }

        return buildElasticSearchTimeFormatFromDouble((getUTCTimestampWithMilliseconds()));
    }

    public static String buildElasticSearchTimeFormatFromDouble(double timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis((long) (1000 * timestamp));

        return String.format("%1$tY-%1$tm-%1$td %1$tH-%1$tM-%1$tS.%1$tL", cal); // ramtamtam
    }

    /**
     * Accepts our ElasticSearch time formats without milliseconds.
     *
     * @return A DateTimeFormtter suitable to parse an ES_DATE_FORMAT formatted string to a
     *         DateTime Object even if it contains no milliseconds.
     */
    public static DateTimeFormatter timeFormatterWithOptionalMilliseconds() {
        // This is the .SSS part
        DateTimeParser ms = new DateTimeFormatterBuilder()
                .appendLiteral(".")
                .appendFractionOfSecond(1,3)
                .toParser();

        return new DateTimeFormatterBuilder()
                .append(DateTimeFormat.forPattern(ES_DATE_FORMAT_NO_MS))
                .appendOptional(ms)
                .toFormatter();
    }

    public static String buildElasticSearchTimeFormatFromDateTime(DateTime d) {
        return d.toString(DateTimeFormat.forPattern(ES_DATE_FORMAT));
    }

    public static int getTimestampOfMessage(SearchHit msg) {
        Object field = msg.getSource().get("timestamp");
        if (field == null) {
            throw new RuntimeException("Document has no field timestamp.");
        }

        DateTimeFormatter formatter = DateTimeFormat.forPattern(ES_DATE_FORMAT);
        DateTime dt = formatter.parseDateTime(field.toString());

        return (int) (dt.getMillis()/1000);
    }

    public static DateTime iso8601() {
        return new DateTime(DateTimeZone.UTC);
    }

    public static String getISO8601String(DateTime time) {
        return ISODateTimeFormat.dateTime().print(time);
    }

    public static String getCurrentISO8601String() {
        return getISO8601String(new DateTime(DateTimeZone.UTC));
    }

    /**
     *
     * @param target String to cut.
     * @param start  Character position to start cutting at. Inclusive.
     * @param end Character position to stop cutting at. Exclusive!
     * @return Extracted/cut part of the string or null when invalid positions where provided.
     */
    public static String safeSubstring(String target, int start, int end) {
        if (target == null) {
            return null;
        }

        int slen = target.length();
        if (start < 0 || end <= 0 || end <= start || slen < start || slen < end) {
            return null;
        }

        return target.substring(start, end);
    }

    /**
     * Convert something to an int in a fast way having a good guess
     * that it is an int. This is perfect for MongoDB data that *should*
     * have been stored as integers already so there is a high probability
     * of easy converting.
     *
     * @param x The object to convert to an int
     * @return Converted object, 0 if empty or something went wrong.
     */
    public static Integer getInt(Object x) {
        if (x == null) {
            return null;
        }

        if (x instanceof Integer) {
            return (Integer) x;
        }

        if (x instanceof String) {
            String s = x.toString();
            if (s == null || s.isEmpty()) {
                return null;
            }
        }

        /*
         * This is the last and probably expensive fallback. This should be avoided by
         * only passing in Integers, Longs or stuff that can be parsed from it's String
         * representation. You might have to build cached objects that did a safe conversion
         * once for example. There is no way around for the actual values we compare if the
         * user sent them in as non-numerical type.
         */
        return Ints.tryParse(x.toString());
    }
 
}
