/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.restclient.lib;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static org.junit.Assert.fail;

public class AsyncByteBufferInputStreamTest {

    @Test
    public void testSyncRead() throws InterruptedException, IOException {
        final AsyncByteBufferInputStream stream = new AsyncByteBufferInputStream();

        stream.putBuffer(ByteBuffer.wrap("123".getBytes()));
        stream.putBuffer(ByteBuffer.wrap("456".getBytes()));

        // we should be able to read 6 bytes in a row
        byte[] bytes = new byte[6];
        final int read = stream.read(bytes);
        Assert.assertEquals(6, read);
        Assert.assertArrayEquals("123456".getBytes(), bytes);
        stream.setDone(true);

        final int nextRead = stream.read();
        Assert.assertEquals(-1, nextRead);
        stream.close();
    }

    @Test
    public void testDoneWithMoreData() throws InterruptedException, IOException {
        final AsyncByteBufferInputStream stream = new AsyncByteBufferInputStream();

        stream.putBuffer(ByteBuffer.wrap("123".getBytes()));
        stream.putBuffer(ByteBuffer.wrap("456".getBytes()));

        // we should be able to read 6 bytes in a row
        byte[] bytes = new byte[6];
        final int read = stream.read(bytes);
        Assert.assertEquals(6, read);
        Assert.assertArrayEquals("123456".getBytes(), bytes);

        stream.putBuffer(ByteBuffer.wrap("789".getBytes()));
        stream.setDone(true);

        byte[] finalBytes = new byte[3];
        final int nextRead = stream.read(finalBytes);
        Assert.assertEquals(3, nextRead);
        Assert.assertArrayEquals("789".getBytes(), finalBytes);

        final int eos = stream.read();
        Assert.assertEquals(-1, eos);

        stream.close();
    }

    @Test
    public void testAsync() throws InterruptedException {
        final AsyncByteBufferInputStream stream = new AsyncByteBufferInputStream();

        final Thread writer = new Thread() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    try {
                        stream.putBuffer(ByteBuffer.wrap("12345".getBytes()));
                        stream.putBuffer(ByteBuffer.wrap("6\n".getBytes()));
                        System.out.println("Wrote buffers step " + i);
                    } catch (InterruptedException ignored) {

                    }
                    sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
                }
                stream.setDone(true);

            }
        };
        final Thread reader = new Thread() {
            @Override
            public void run() {
                int singleByte = -1;
                int count = 0;
                do {
                    try {
                        singleByte = stream.read();
                        count++;
                    } catch (IOException e) {

                    }
                    if (count % 5 == 0) {
                        System.out.println("read " + count + " bytes");
                    }
                } while (singleByte != -1);
            }
        };
        reader.start();
        writer.start();
        reader.join();
        writer.join();
    }

    @Test
    public void testAsyncException() throws InterruptedException {
        final AsyncByteBufferInputStream stream = new AsyncByteBufferInputStream();

        final Thread writer = new Thread() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    try {
                        stream.putBuffer(ByteBuffer.wrap("12345".getBytes()));
                        stream.putBuffer(ByteBuffer.wrap("6\n".getBytes()));
                        System.out.println("Wrote buffers step " + i);
                    } catch (InterruptedException ignored) {

                    }
                    sleepUninterruptibly(500, TimeUnit.MILLISECONDS);
                    if (i == 3) {
                        stream.setFailed(new Throwable());
                        return;
                    }
                }

            }
        };
        final Thread reader = new Thread() {
            @Override
            public void run() {
                int singleByte;
                int count = 0;
                do {
                    try {
                        singleByte = stream.read();
                        count++;
                    } catch (IOException e) {
                        System.out.println("Caught exception, success.");
                        return;
                    }
                    if (count % 5 == 0) {
                        System.out.println("read " + count + " bytes");
                    }
                    if (count > 50) {
                        fail("Should've caught IOException.");
                    }
                } while (singleByte != -1);
            }
        };
        reader.start();
        writer.start();
        reader.join();
        writer.join();
    }


}