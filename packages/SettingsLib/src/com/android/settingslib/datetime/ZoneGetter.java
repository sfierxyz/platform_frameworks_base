/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.settingslib.datetime;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.icu.text.TimeZoneNames;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.android.settingslib.R;

import org.xmlpull.v1.XmlPullParserException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * ZoneGetter is the utility class to get time zone and zone list, and both of them have display
 * name in time zone. In this class, we will keep consistency about display names for all
 * the methods.
 *
 * The display name chosen for each zone entry depends on whether the zone is one associated
 * with the country of the user's chosen locale. For "local" zones we prefer the "long name"
 * (e.g. "Europe/London" -> "British Summer Time" for people in the UK). For "non-local"
 * zones we prefer the exemplar location (e.g. "Europe/London" -> "London" for English
 * speakers from outside the UK). This heuristic is based on the fact that people are
 * typically familiar with their local timezones and exemplar locations don't always match
 * modern-day expectations for people living in the country covered. Large countries like
 * China that mostly use a single timezone (olson id: "Asia/Shanghai") may not live near
 * "Shanghai" and prefer the long name over the exemplar location. The only time we don't
 * follow this policy for local zones is when Android supplies multiple olson IDs to choose
 * from and the use of a zone's long name leads to ambiguity. For example, at the time of
 * writing Android lists 5 olson ids for Australia which collapse to 2 different zone names
 * in winter but 4 different zone names in summer. The ambiguity leads to the users
 * selecting the wrong olson ids.
 *
 */
public class ZoneGetter {
    private static final String TAG = "ZoneGetter";

    public static final String KEY_ID = "id";  // value: String
    public static final String KEY_DISPLAYNAME = "name";  // value: String
    public static final String KEY_GMT = "gmt";  // value: String
    public static final String KEY_OFFSET = "offset";  // value: int (Integer)

    private static final String XMLTAG_TIMEZONE = "timezone";

    public static String getTimeZoneOffsetAndName(Context context, TimeZone tz, Date now) {
        final Locale locale = Locale.getDefault();
        final String gmtString = getGmtOffsetString(locale, tz, now);
        final TimeZoneNames timeZoneNames = TimeZoneNames.getInstance(locale);
        final ZoneGetterData data = new ZoneGetterData(context);

        final boolean useExemplarLocationForLocalNames =
                shouldUseExemplarLocationForLocalNames(data, timeZoneNames);
        final String zoneNameString = getTimeZoneDisplayName(data, timeZoneNames,
                useExemplarLocationForLocalNames, tz, tz.getID());
        if (zoneNameString == null) {
            return gmtString;
        }

        // We don't use punctuation here to avoid having to worry about localizing that too!
        return gmtString + " " + zoneNameString;
    }

    public static List<Map<String, Object>> getZonesList(Context context) {
        final Locale locale = Locale.getDefault();
        final Date now = new Date();
        final TimeZoneNames timeZoneNames = TimeZoneNames.getInstance(locale);
        final ZoneGetterData data = new ZoneGetterData(context);

        // Work out whether the display names we would show by default would be ambiguous.
        final boolean useExemplarLocationForLocalNames =
                shouldUseExemplarLocationForLocalNames(data, timeZoneNames);

        // Generate the list of zone entries to return.
        List<Map<String, Object>> zones = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < data.zoneCount; i++) {
            TimeZone tz = data.timeZones[i];
            String gmtOffsetString = data.gmtOffsetStrings[i];

            String displayName = getTimeZoneDisplayName(data, timeZoneNames,
                    useExemplarLocationForLocalNames, tz, data.olsonIdsToDisplay[i]);
            if (displayName == null  || displayName.isEmpty()) {
                displayName = gmtOffsetString;
            }

            int offsetMillis = tz.getOffset(now.getTime());
            Map<String, Object> displayEntry =
                    createDisplayEntry(tz, gmtOffsetString, displayName, offsetMillis);
            zones.add(displayEntry);
        }
        return zones;
    }

    private static Map<String, Object> createDisplayEntry(
            TimeZone tz, String gmtOffsetString, String displayName, int offsetMillis) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put(KEY_ID, tz.getID());
        map.put(KEY_DISPLAYNAME, displayName);
        map.put(KEY_GMT, gmtOffsetString);
        map.put(KEY_OFFSET, offsetMillis);
        return map;
    }

    private static List<String> readTimezonesToDisplay(Context context) {
        List<String> olsonIds = new ArrayList<String>();
        try (XmlResourceParser xrp = context.getResources().getXml(R.xml.timezones)) {
            while (xrp.next() != XmlResourceParser.START_TAG) {
                continue;
            }
            xrp.next();
            while (xrp.getEventType() != XmlResourceParser.END_TAG) {
                while (xrp.getEventType() != XmlResourceParser.START_TAG) {
                    if (xrp.getEventType() == XmlResourceParser.END_DOCUMENT) {
                        return olsonIds;
                    }
                    xrp.next();
                }
                if (xrp.getName().equals(XMLTAG_TIMEZONE)) {
                    String olsonId = xrp.getAttributeValue(0);
                    olsonIds.add(olsonId);
                }
                while (xrp.getEventType() != XmlResourceParser.END_TAG) {
                    xrp.next();
                }
                xrp.next();
            }
        } catch (XmlPullParserException xppe) {
            Log.e(TAG, "Ill-formatted timezones.xml file");
        } catch (java.io.IOException ioe) {
            Log.e(TAG, "Unable to read timezones.xml file");
        }
        return olsonIds;
    }

    private static boolean shouldUseExemplarLocationForLocalNames(ZoneGetterData data,
            TimeZoneNames timeZoneNames) {
        final Set<String> localZoneNames = new HashSet<String>();
        final Date now = new Date();
        for (int i = 0; i < data.zoneCount; i++) {
            final String olsonId = data.olsonIdsToDisplay[i];
            if (data.localZoneIds.contains(olsonId)) {
                final TimeZone tz = data.timeZones[i];
                String displayName = getZoneLongName(timeZoneNames, tz, now);
                if (displayName == null) {
                    displayName = data.gmtOffsetStrings[i];
                }
                final boolean nameIsUnique = localZoneNames.add(displayName);
                if (!nameIsUnique) {
                    return true;
                }
            }
        }

        return false;
    }

    private static String getTimeZoneDisplayName(ZoneGetterData data, TimeZoneNames timeZoneNames,
            boolean useExemplarLocationForLocalNames, TimeZone tz, String olsonId) {
        final Date now = new Date();
        final boolean isLocalZoneId = data.localZoneIds.contains(olsonId);
        final boolean preferLongName = isLocalZoneId && !useExemplarLocationForLocalNames;
        String displayName;

        if (preferLongName) {
            displayName = getZoneLongName(timeZoneNames, tz, now);
        } else {
            displayName = timeZoneNames.getExemplarLocationName(tz.getID());
            if (displayName == null || displayName.isEmpty()) {
                // getZoneExemplarLocation can return null. Fall back to the long name.
                displayName = getZoneLongName(timeZoneNames, tz, now);
            }
        }

        return displayName;
    }

    /**
     * Returns the long name for the timezone for the given locale at the time specified.
     * Can return {@code null}.
     */
    private static String getZoneLongName(TimeZoneNames names, TimeZone tz, Date now) {
        final TimeZoneNames.NameType nameType =
                tz.inDaylightTime(now) ? TimeZoneNames.NameType.LONG_DAYLIGHT
                        : TimeZoneNames.NameType.LONG_STANDARD;
        return names.getDisplayName(tz.getID(), nameType, now.getTime());
    }

    private static String getGmtOffsetString(Locale locale, TimeZone tz, Date now) {
        // Use SimpleDateFormat to format the GMT+00:00 string.
        final SimpleDateFormat gmtFormatter = new SimpleDateFormat("ZZZZ");
        gmtFormatter.setTimeZone(tz);
        String gmtString = gmtFormatter.format(now);

        // Ensure that the "GMT+" stays with the "00:00" even if the digits are RTL.
        final BidiFormatter bidiFormatter = BidiFormatter.getInstance();
        boolean isRtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL;
        gmtString = bidiFormatter.unicodeWrap(gmtString,
                isRtl ? TextDirectionHeuristics.RTL : TextDirectionHeuristics.LTR);
        return gmtString;
    }

    private static final class ZoneGetterData {
        public final String[] olsonIdsToDisplay;
        public final String[] gmtOffsetStrings;
        public final TimeZone[] timeZones;
        public final Set<String> localZoneIds;
        public final int zoneCount;

        public ZoneGetterData(Context context) {
            final Locale locale = Locale.getDefault();
            final Date now = new Date();
            final List<String> olsonIdsToDisplayList = readTimezonesToDisplay(context);

            // Load all the data needed to display time zones
            zoneCount = olsonIdsToDisplayList.size();
            olsonIdsToDisplay = new String[zoneCount];
            timeZones = new TimeZone[zoneCount];
            gmtOffsetStrings = new String[zoneCount];
            for (int i = 0; i < zoneCount; i++) {
                final String olsonId = olsonIdsToDisplayList.get(i);
                olsonIdsToDisplay[i] = olsonId;
                final TimeZone tz = TimeZone.getTimeZone(olsonId);
                timeZones[i] = tz;
                gmtOffsetStrings[i] = getGmtOffsetString(locale, tz, now);
            }

            // Create a lookup of local zone IDs.
            localZoneIds = new HashSet<String>();
            for (String olsonId : libcore.icu.TimeZoneNames.forLocale(locale)) {
                localZoneIds.add(olsonId);
            }
        }
    }
}