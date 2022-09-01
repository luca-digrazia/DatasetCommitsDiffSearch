// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.bazel.repository;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.Proxy;

/**
 * Tests for @{link HttpDownloader}.
 */
@RunWith(JUnit4.class)
public class HttpDownloaderTest {
  @Test
  public void testNoProxy() throws Exception {
    // Empty address.
    Proxy proxy = HttpDownloader.createProxy(null);
    assertEquals(Proxy.NO_PROXY, proxy);
    proxy = HttpDownloader.createProxy("");
    assertEquals(Proxy.NO_PROXY, proxy);
  }

  @Test
  public void testProxyDefaultPort() throws Exception {
    Proxy proxy = HttpDownloader.createProxy("http://my.example.com");
    assertEquals(Proxy.Type.HTTP, proxy.type());
    assertThat(proxy.toString()).endsWith(":80");

    proxy = HttpDownloader.createProxy("https://my.example.com");
    assertThat(proxy.toString()).endsWith(":443");
  }

  @Test
  public void testProxyExplicitPort() throws Exception {
    Proxy proxy = HttpDownloader.createProxy("http://my.example.com:12345");
    assertThat(proxy.toString()).endsWith(":12345");

    proxy = HttpDownloader.createProxy("https://my.example.com:12345");
    assertThat(proxy.toString()).endsWith(":12345");
  }

  @Test
  public void testProxyNoProtocol() throws Exception {
    try {
      HttpDownloader.createProxy("my.example.com");
      fail("Expected protocol error");
    } catch (IOException e) {
      assertThat(e.getMessage()).contains("No proxy protocol found");
    }
  }

  @Test
  public void testProxyNoProtocolWithPort() throws Exception {
    try {
      HttpDownloader.createProxy("my.example.com:12345");
      fail("Expected protocol error");
    } catch (IOException e) {
      assertThat(e.getMessage()).contains("Invalid proxy protocol");
    }
  }

  @Test
  public void testProxyPortParsingError() throws Exception {
    try {
      HttpDownloader.createProxy("http://my.example.com:foo");
      fail("Should have thrown an error for invalid port");
    } catch (IOException e) {
      assertThat(e.getMessage()).contains("Error parsing proxy port");
    }
  }

}
