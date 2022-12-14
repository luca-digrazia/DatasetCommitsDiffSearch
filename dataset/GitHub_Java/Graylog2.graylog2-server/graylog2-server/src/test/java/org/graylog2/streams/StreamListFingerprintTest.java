/**
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
 */

package org.graylog2.streams;

import autovalue.shaded.com.google.common.common.collect.Sets;
import com.google.common.collect.Lists;
import org.graylog2.plugin.streams.Output;
import org.graylog2.plugin.streams.Stream;
import org.graylog2.plugin.streams.StreamRule;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class StreamListFingerprintTest {
    @Mock Stream stream1;
    @Mock Stream stream2;
    @Mock StreamRule streamRule1;
    @Mock StreamRule streamRule2;
    @Mock StreamRule streamRule3;
    @Mock Output output1;
    @Mock Output output2;

    private final String expectedFingerprint = "8131e74d28b9068473e8b57517c03a5ea1158402";
    private final String expectedEmptyFingerprint = "da39a3ee5e6b4b0d3255bfef95601890afd80709";

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(stream1.getId()).thenReturn("stream-xyz");
        when(stream2.getId()).thenReturn("stream-abc");
        when(streamRule1.getId()).thenReturn("rule-abc");
        when(streamRule2.getId()).thenReturn("rule-def");
        when(streamRule3.getId()).thenReturn("rule-xyz");
        when(output1.getId()).thenReturn("output-def");
        when(output2.getId()).thenReturn("output-xyz");

        when(stream1.getStreamRules()).thenReturn(Lists.newArrayList(streamRule1, streamRule2));
        when(stream2.getStreamRules()).thenReturn(Lists.newArrayList(streamRule3));
        
        when(stream1.getOutputs()).thenReturn(Sets.newHashSet(output1, output2));
        when(stream2.getOutputs()).thenReturn(Sets.newHashSet(output2, output1));
    }

    @Test
    public void testGetFingerprint() throws Exception {
        final StreamListFingerprint fingerprint = new StreamListFingerprint(Lists.newArrayList(stream1, stream2));

        assertEquals(fingerprint.getFingerprint(), expectedFingerprint);
    }

    @Test
    public void testWithEmptyStreamList() throws Exception {
        final StreamListFingerprint fingerprint = new StreamListFingerprint(Lists.<Stream>newArrayList());

        assertEquals(fingerprint.getFingerprint(), expectedEmptyFingerprint);
    }
}