/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License.
 */

package android.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Typeface;
import android.platform.test.annotations.Presubmit;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.filters.Suppress;
import android.support.test.runner.AndroidJUnit4;
import android.text.Layout.TabStops;
import android.text.style.TabStopSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextLineTest {
    private boolean stretchesToFullWidth(CharSequence line) {
        final TextPaint paint = new TextPaint();
        final TextLine tl = TextLine.obtain();
        tl.set(paint, line, 0, line.length(), Layout.DIR_LEFT_TO_RIGHT,
                Layout.DIRS_ALL_LEFT_TO_RIGHT, false /* hasTabs */, null /* tabStops */);
        final float originalWidth = tl.metrics(null);
        final float expandedWidth = 2 * originalWidth;

        tl.justify(expandedWidth);
        final float newWidth = tl.metrics(null);
        TextLine.recycle(tl);
        return Math.abs(newWidth - expandedWidth) < 0.5;
    }

    @Test
    public void testJustify_spaces() {
        // There are no spaces to stretch.
        assertFalse(stretchesToFullWidth("text"));

        assertTrue(stretchesToFullWidth("one space"));
        assertTrue(stretchesToFullWidth("exactly two spaces"));
        assertTrue(stretchesToFullWidth("up to three spaces"));
    }

    // NBSP should also stretch when it's not used as a base for a combining mark. This doesn't work
    // yet (b/68204709).
    @Suppress
    public void disabledTestJustify_NBSP() {
        final char nbsp = '\u00A0';
        assertTrue(stretchesToFullWidth("non-breaking" + nbsp + "space"));
        assertTrue(stretchesToFullWidth("mix" + nbsp + "and match"));

        final char combining_acute = '\u0301';
        assertFalse(stretchesToFullWidth("combining" + nbsp + combining_acute + "acute"));
    }

    // The test font has following coverage and width.
    // U+0020: 10em
    // U+002E (.): 10em
    // U+0043 (C): 100em
    // U+0049 (I): 1em
    // U+004C (L): 50em
    // U+0056 (V): 5em
    // U+0058 (X): 10em
    // U+005F (_): 0em
    // U+05D0    : 1em  // HEBREW LETTER ALEF
    // U+05D1    : 5em  // HEBREW LETTER BET
    // U+FFFD (invalid surrogate will be replaced to this): 7em
    // U+10331 (\uD800\uDF31): 10em
    private static final Typeface TYPEFACE = Typeface.createFromAsset(
            InstrumentationRegistry.getInstrumentation().getTargetContext().getAssets(),
            "fonts/StaticLayoutLineBreakingTestFont.ttf");

    private TextLine getTextLine(String str, TextPaint paint, TabStops tabStops) {
        Layout layout = StaticLayout.Builder.obtain(str, 0, str.length(), paint, Integer.MAX_VALUE)
                .build();
        TextLine tl = TextLine.obtain();
        tl.set(paint, str, 0, str.length(),
                TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(str, 0, str.length()) ? -1 : 1,
                layout.getLineDirections(0), tabStops != null, tabStops);
        return tl;
    }

    private TextLine getTextLine(String str, TextPaint paint) {
        return getTextLine(str, paint, null);
    }

    @Test
    public void testMeasure_LTR() {
        final TextPaint paint = new TextPaint();
        paint.setTypeface(TYPEFACE);
        paint.setTextSize(10.0f);  // make 1em = 10px

        TextLine tl = getTextLine("IIIIIV", paint);
        assertEquals(0.0f, tl.measure(0, false, null), 0.0f);
        assertEquals(10.0f, tl.measure(1, false, null), 0.0f);
        assertEquals(20.0f, tl.measure(2, false, null), 0.0f);
        assertEquals(30.0f, tl.measure(3, false, null), 0.0f);
        assertEquals(40.0f, tl.measure(4, false, null), 0.0f);
        assertEquals(50.0f, tl.measure(5, false, null), 0.0f);
        assertEquals(100.0f, tl.measure(6, false, null), 0.0f);

        assertEquals(0.0f, tl.measure(0, true, null), 0.0f);
        assertEquals(10.0f, tl.measure(1, true, null), 0.0f);
        assertEquals(20.0f, tl.measure(2, true, null), 0.0f);
        assertEquals(30.0f, tl.measure(3, true, null), 0.0f);
        assertEquals(40.0f, tl.measure(4, true, null), 0.0f);
        assertEquals(50.0f, tl.measure(5, true, null), 0.0f);
        assertEquals(100.0f, tl.measure(6, true, null), 0.0f);
    }

    @Test
    public void testMeasure_RTL() {
        final TextPaint paint = new TextPaint();
        paint.setTypeface(TYPEFACE);
        paint.setTextSize(10.0f);  // make 1em = 10px

        TextLine tl = getTextLine("\u05D0\u05D0\u05D0\u05D0\u05D0\u05D1", paint);
        assertEquals(0.0f, tl.measure(0, false, null), 0.0f);
        assertEquals(-10.0f, tl.measure(1, false, null), 0.0f);
        assertEquals(-20.0f, tl.measure(2, false, null), 0.0f);
        assertEquals(-30.0f, tl.measure(3, false, null), 0.0f);
        assertEquals(-40.0f, tl.measure(4, false, null), 0.0f);
        assertEquals(-50.0f, tl.measure(5, false, null), 0.0f);
        assertEquals(-100.0f, tl.measure(6, false, null), 0.0f);

        assertEquals(0.0f, tl.measure(0, true, null), 0.0f);
        assertEquals(-10.0f, tl.measure(1, true, null), 0.0f);
        assertEquals(-20.0f, tl.measure(2, true, null), 0.0f);
        assertEquals(-30.0f, tl.measure(3, true, null), 0.0f);
        assertEquals(-40.0f, tl.measure(4, true, null), 0.0f);
        assertEquals(-50.0f, tl.measure(5, true, null), 0.0f);
        assertEquals(-100.0f, tl.measure(6, true, null), 0.0f);
    }

    @Test
    public void testMeasure_BiDi() {
        final TextPaint paint = new TextPaint();
        paint.setTypeface(TYPEFACE);
        paint.setTextSize(10.0f);  // make 1em = 10px

        TextLine tl = getTextLine("II\u05D0\u05D0II", paint);
        assertEquals(0.0f, tl.measure(0, false, null), 0.0f);
        assertEquals(10.0f, tl.measure(1, false, null), 0.0f);
        assertEquals(40.0f, tl.measure(2, false, null), 0.0f);
        assertEquals(30.0f, tl.measure(3, false, null), 0.0f);
        assertEquals(40.0f, tl.measure(4, false, null), 0.0f);
        assertEquals(50.0f, tl.measure(5, false, null), 0.0f);
        assertEquals(60.0f, tl.measure(6, false, null), 0.0f);

        assertEquals(0.0f, tl.measure(0, true, null), 0.0f);
        assertEquals(10.0f, tl.measure(1, true, null), 0.0f);
        assertEquals(20.0f, tl.measure(2, true, null), 0.0f);
        assertEquals(30.0f, tl.measure(3, true, null), 0.0f);
        assertEquals(20.0f, tl.measure(4, true, null), 0.0f);
        assertEquals(50.0f, tl.measure(5, true, null), 0.0f);
        assertEquals(60.0f, tl.measure(6, true, null), 0.0f);
    }

    private static final String LRI = "\u2066";  // LEFT-TO-RIGHT ISOLATE
    private static final String RLI = "\u2067";  // RIGHT-TO-LEFT ISOLATE
    private static final String PDI = "\u2069";  // POP DIRECTIONAL ISOLATE

    @Test
    public void testMeasure_BiDi2() {
        final TextPaint paint = new TextPaint();
        paint.setTypeface(TYPEFACE);
        paint.setTextSize(10.0f);  // make 1em = 10px

        TextLine tl = getTextLine("I" + RLI + "I\u05D0\u05D0" + PDI + "I", paint);
        assertEquals(0.0f, tl.measure(0, false, null), 0.0f);
        assertEquals(10.0f, tl.measure(1, false, null), 0.0f);
        assertEquals(30.0f, tl.measure(2, false, null), 0.0f);
        assertEquals(30.0f, tl.measure(3, false, null), 0.0f);
        assertEquals(20.0f, tl.measure(4, false, null), 0.0f);
        assertEquals(40.0f, tl.measure(5, false, null), 0.0f);
        assertEquals(40.0f, tl.measure(6, false, null), 0.0f);
        assertEquals(50.0f, tl.measure(7, false, null), 0.0f);

        assertEquals(0.0f, tl.measure(0, true, null), 0.0f);
        assertEquals(10.0f, tl.measure(1, true, null), 0.0f);
        assertEquals(10.0f, tl.measure(2, true, null), 0.0f);
        assertEquals(40.0f, tl.measure(3, true, null), 0.0f);
        assertEquals(20.0f, tl.measure(4, true, null), 0.0f);
        assertEquals(10.0f, tl.measure(5, true, null), 0.0f);
        assertEquals(40.0f, tl.measure(6, true, null), 0.0f);
        assertEquals(50.0f, tl.measure(7, true, null), 0.0f);
    }

    @Test
    public void testMeasure_BiDi3() {
        final TextPaint paint = new TextPaint();
        paint.setTypeface(TYPEFACE);
        paint.setTextSize(10.0f);  // make 1em = 10px

        TextLine tl = getTextLine("\u05D0" + LRI + "\u05D0II" + PDI + "\u05D0", paint);
        assertEquals(0.0f, tl.measure(0, false, null), 0.0f);
        assertEquals(-10.0f, tl.measure(1, false, null), 0.0f);
        assertEquals(-30.0f, tl.measure(2, false, null), 0.0f);
        assertEquals(-30.0f, tl.measure(3, false, null), 0.0f);
        assertEquals(-20.0f, tl.measure(4, false, null), 0.0f);
        assertEquals(-40.0f, tl.measure(5, false, null), 0.0f);
        assertEquals(-40.0f, tl.measure(6, false, null), 0.0f);
        assertEquals(-50.0f, tl.measure(7, false, null), 0.0f);

        assertEquals(0.0f, tl.measure(0, true, null), 0.0f);
        assertEquals(-10.0f, tl.measure(1, true, null), 0.0f);
        assertEquals(-10.0f, tl.measure(2, true, null), 0.0f);
        assertEquals(-40.0f, tl.measure(3, true, null), 0.0f);
        assertEquals(-20.0f, tl.measure(4, true, null), 0.0f);
        assertEquals(-10.0f, tl.measure(5, true, null), 0.0f);
        assertEquals(-40.0f, tl.measure(6, true, null), 0.0f);
        assertEquals(-50.0f, tl.measure(7, true, null), 0.0f);
    }

    @Test
    public void testMeasure_Tab_LTR() {
        final Object[] spans = { new TabStopSpan.Standard(100) };
        final TabStops stops = new TabStops(100, spans);
        final TextPaint paint = new TextPaint();
        paint.setTypeface(TYPEFACE);
        paint.setTextSize(10.0f);  // make 1em = 10px

        TextLine tl = getTextLine("II\tII", paint, stops);
        assertEquals(0.0f, tl.measure(0, false, null), 0.0f);
        assertEquals(10.0f, tl.measure(1, false, null), 0.0f);
        assertEquals(20.0f, tl.measure(2, false, null), 0.0f);
        assertEquals(100.0f, tl.measure(3, false, null), 0.0f);
        assertEquals(110.0f, tl.measure(4, false, null), 0.0f);

        assertEquals(0.0f, tl.measure(0, true, null), 0.0f);
        assertEquals(10.0f, tl.measure(1, true, null), 0.0f);
        assertEquals(20.0f, tl.measure(2, true, null), 0.0f);
        assertEquals(100.0f, tl.measure(3, true, null), 0.0f);
        assertEquals(110.0f, tl.measure(4, true, null), 0.0f);
    }

    @Test
    public void testMeasure_Tab_RTL() {
        final Object[] spans = { new TabStopSpan.Standard(100) };
        final TabStops stops = new TabStops(100, spans);
        final TextPaint paint = new TextPaint();
        paint.setTypeface(TYPEFACE);
        paint.setTextSize(10.0f);  // make 1em = 10px

        TextLine tl = getTextLine("\u05D0\u05D0\t\u05D0\u05D0", paint, stops);
        assertEquals(0.0f, tl.measure(0, false, null), 0.0f);
        assertEquals(-10.0f, tl.measure(1, false, null), 0.0f);
        assertEquals(-20.0f, tl.measure(2, false, null), 0.0f);
        assertEquals(-100.0f, tl.measure(3, false, null), 0.0f);
        assertEquals(-110.0f, tl.measure(4, false, null), 0.0f);

        assertEquals(0.0f, tl.measure(0, true, null), 0.0f);
        assertEquals(-10.0f, tl.measure(1, true, null), 0.0f);
        assertEquals(-20.0f, tl.measure(2, true, null), 0.0f);
        assertEquals(-100.0f, tl.measure(3, true, null), 0.0f);
        assertEquals(-110.0f, tl.measure(4, true, null), 0.0f);
    }

    @Test
    public void testMeasure_Tab_BiDi() {
        final Object[] spans = { new TabStopSpan.Standard(100) };
        final TabStops stops = new TabStops(100, spans);
        final TextPaint paint = new TextPaint();
        paint.setTypeface(TYPEFACE);
        paint.setTextSize(10.0f);  // make 1em = 10px

        TextLine tl = getTextLine("I\u05D0\tI\u05D0", paint, stops);
        assertEquals(0.0f, tl.measure(0, false, null), 0.0f);
        assertEquals(20.0f, tl.measure(1, false, null), 0.0f);
        assertEquals(20.0f, tl.measure(2, false, null), 0.0f);
        assertEquals(100.0f, tl.measure(3, false, null), 0.0f);
        assertEquals(120.0f, tl.measure(4, false, null), 0.0f);
        assertEquals(120.0f, tl.measure(5, false, null), 0.0f);

        assertEquals(0.0f, tl.measure(0, true, null), 0.0f);
        assertEquals(10.0f, tl.measure(1, true, null), 0.0f);
        assertEquals(10.0f, tl.measure(2, true, null), 0.0f);
        assertEquals(100.0f, tl.measure(3, true, null), 0.0f);
        assertEquals(110.0f, tl.measure(4, true, null), 0.0f);
        assertEquals(110.0f, tl.measure(5, true, null), 0.0f);
    }

    @Test
    public void testMeasure_Tab_BiDi2() {
        final Object[] spans = { new TabStopSpan.Standard(100) };
        final TabStops stops = new TabStops(100, spans);
        final TextPaint paint = new TextPaint();
        paint.setTypeface(TYPEFACE);
        paint.setTextSize(10.0f);  // make 1em = 10px

        TextLine tl = getTextLine("\u05D0I\t\u05D0I", paint, stops);
        assertEquals(0.0f, tl.measure(0, false, null), 0.0f);
        assertEquals(-20.0f, tl.measure(1, false, null), 0.0f);
        assertEquals(-20.0f, tl.measure(2, false, null), 0.0f);
        assertEquals(-100.0f, tl.measure(3, false, null), 0.0f);
        assertEquals(-120.0f, tl.measure(4, false, null), 0.0f);
        assertEquals(-120.0f, tl.measure(5, false, null), 0.0f);

        assertEquals(-0.0f, tl.measure(0, true, null), 0.0f);
        assertEquals(-10.0f, tl.measure(1, true, null), 0.0f);
        assertEquals(-10.0f, tl.measure(2, true, null), 0.0f);
        assertEquals(-100.0f, tl.measure(3, true, null), 0.0f);
        assertEquals(-110.0f, tl.measure(4, true, null), 0.0f);
        assertEquals(-110.0f, tl.measure(5, true, null), 0.0f);
    }
}
