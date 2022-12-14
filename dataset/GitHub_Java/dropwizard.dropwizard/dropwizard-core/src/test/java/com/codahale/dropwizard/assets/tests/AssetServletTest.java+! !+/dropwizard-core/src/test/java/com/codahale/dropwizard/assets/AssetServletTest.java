package com.codahale.dropwizard.assets.tests;

import com.codahale.dropwizard.assets.AssetServlet;
import com.google.common.net.HttpHeaders;
import org.eclipse.jetty.http.*;
import org.eclipse.jetty.servlet.ServletTester;
import org.fest.assertions.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AssetServletTest {
    private static final String DUMMY_SERVLET = "/dummy_servlet/";
    private static final String NOINDEX_SERVLET = "/noindex_servlet/";
    private static final String NOCHARSET_SERVLET = "/nocharset_servlet/";
    private static final String ROOT_SERVLET = "/";
    private static final String RESOURCE_PATH = "/assets";

    // ServletTester expects to be able to instantiate the servlet with zero arguments

    public static class DummyAssetServlet extends AssetServlet {
        private static final long serialVersionUID = -1L;

        public DummyAssetServlet() {
            super(RESOURCE_PATH, DUMMY_SERVLET, "index.htm");
        }
    }

    public static class NoIndexAssetServlet extends AssetServlet {
        private static final long serialVersionUID = -1L;

        public NoIndexAssetServlet() {
            super(RESOURCE_PATH, DUMMY_SERVLET, null);
        }
    }

    public static class RootAssetServlet extends AssetServlet {
        public RootAssetServlet() {
            super("/", ROOT_SERVLET, null);
        }
    }

    public static class NoCharsetAssetServlet extends AssetServlet {
        public NoCharsetAssetServlet() {
            super(RESOURCE_PATH, NOCHARSET_SERVLET, null);
            setDefaultCharset(null);
        }
    }

    private final ServletTester servletTester = new ServletTester();
    private final HttpTester.Request request = HttpTester.newRequest();
    private HttpTester.Response response;

    @Before
    public void setup() throws Exception {
        servletTester.addServlet(DummyAssetServlet.class, DUMMY_SERVLET + '*');
        servletTester.addServlet(NoIndexAssetServlet.class, NOINDEX_SERVLET + '*');
        servletTester.addServlet(NoCharsetAssetServlet.class, NOCHARSET_SERVLET + '*');
        servletTester.addServlet(RootAssetServlet.class, ROOT_SERVLET + '*');
        servletTester.start();

        request.setMethod("GET");
        request.setURI(DUMMY_SERVLET + "example.txt");
        request.setVersion(HttpVersion.HTTP_1_0);
    }

    @After
    public void tearDown() throws Exception {
        servletTester.stop();
    }

    @Test
    public void servesFilesMappedToRoot() throws Exception {
        request.setURI(ROOT_SERVLET + "assets/example.txt");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(200);
        Assertions.assertThat(response.getContent())
                  .isEqualTo("HELLO THERE");
    }

    @Test
    public void servesCharset() throws Exception {
        request.setURI(DUMMY_SERVLET + "example.txt");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(200);
        Assertions.assertThat(MimeTypes.CACHE.get(response.get(HttpHeader.CONTENT_TYPE)))
                  .isEqualTo(MimeTypes.Type.TEXT_PLAIN_UTF_8);

        request.setURI(NOCHARSET_SERVLET + "example.txt");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(200);
        Assertions.assertThat(response.get(HttpHeader.CONTENT_TYPE))
                  .isEqualTo(MimeTypes.Type.TEXT_PLAIN.toString());
    }

    @Test
    public void servesFilesFromRootsWithSameName() throws Exception {
        request.setURI(DUMMY_SERVLET + "example2.txt");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(200);
        Assertions.assertThat(response.getContent())
                  .isEqualTo("HELLO THERE 2");
    }

    @Test
    public void servesFilesWithA200() throws Exception {
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(200);
        Assertions.assertThat(response.getContent())
                  .isEqualTo("HELLO THERE");
    }

    @Test
    public void throws404IfTheAssetIsMissing() throws Exception {
        request.setURI(DUMMY_SERVLET + "doesnotexist.txt");

        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(404);
    }

    @Test
    public void consistentlyAssignsETags() throws Exception {
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final String firstEtag = response.get(HttpHeaders.ETAG);

        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final String secondEtag = response.get(HttpHeaders.ETAG);

        Assertions.assertThat(firstEtag)
                  .isNotNull();
        Assertions.assertThat(firstEtag)
                  .isNotEmpty();
        Assertions.assertThat(firstEtag)
                  .isEqualTo(secondEtag);
    }

    @Test
    public void assignsDifferentETagsForDifferentFiles() throws Exception {
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final String firstEtag = response.get(HttpHeaders.ETAG);

        request.setURI(DUMMY_SERVLET + "foo.bar");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final String secondEtag = response.get(HttpHeaders.ETAG);

        Assertions.assertThat(firstEtag)
                  .isNotEqualTo(secondEtag);
    }

    @Test
    public void supportsIfNoneMatchRequests() throws Exception {
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final String correctEtag = response.get(HttpHeaders.ETAG);

        request.setHeader(HttpHeaders.IF_NONE_MATCH, correctEtag);
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final int statusWithMatchingEtag = response.getStatus();

        request.setHeader(HttpHeaders.IF_NONE_MATCH, correctEtag + "FOO");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final int statusWithNonMatchingEtag = response.getStatus();

        Assertions.assertThat(statusWithMatchingEtag)
                  .isEqualTo(304);
        Assertions.assertThat(statusWithNonMatchingEtag)
                  .isEqualTo(200);
    }

    @Test
    public void consistentlyAssignsLastModifiedTimes() throws Exception {
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final long firstLastModifiedTime = response.getDateField(HttpHeaders.LAST_MODIFIED);

        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final long secondLastModifiedTime = response.getDateField(HttpHeaders.LAST_MODIFIED);

        Assertions.assertThat(firstLastModifiedTime)
                  .isEqualTo(secondLastModifiedTime);
    }

    @Test
    public void supportsIfModifiedSinceRequests() throws Exception {
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final long lastModifiedTime = response.getDateField(HttpHeaders.LAST_MODIFIED);


        request.setHeader(HttpHeaders.IF_MODIFIED_SINCE, HttpFields.formatDate(lastModifiedTime));
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final int statusWithMatchingLastModifiedTime = response.getStatus();

        request.setHeader(HttpHeaders.IF_MODIFIED_SINCE,
                          HttpFields.formatDate(lastModifiedTime - 100));
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final int statusWithStaleLastModifiedTime = response.getStatus();

        request.setHeader(HttpHeaders.IF_MODIFIED_SINCE,
                          HttpFields.formatDate(lastModifiedTime + 100));
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        final int statusWithRecentLastModifiedTime = response.getStatus();

        Assertions.assertThat(statusWithMatchingLastModifiedTime)
                  .isEqualTo(304);
        Assertions.assertThat(statusWithStaleLastModifiedTime)
                  .isEqualTo(200);
        Assertions.assertThat(statusWithRecentLastModifiedTime)
                  .isEqualTo(304);
    }

    @Test
    public void guessesMimeTypes() throws Exception {
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(200);
        Assertions.assertThat(MimeTypes.CACHE.get(response.get(HttpHeader.CONTENT_TYPE)))
                  .isEqualTo(MimeTypes.Type.TEXT_PLAIN_UTF_8);
    }

    @Test
    public void defaultsToHtml() throws Exception {
        request.setURI(DUMMY_SERVLET + "foo.bar");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(200);
        Assertions.assertThat(MimeTypes.CACHE.get(response.get(HttpHeader.CONTENT_TYPE)))
                  .isEqualTo(MimeTypes.Type.TEXT_HTML_UTF_8);
    }

    @Test
    public void servesIndexFilesByDefault() throws Exception {
        // Root directory listing:
        request.setURI(DUMMY_SERVLET);
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(200);
        Assertions.assertThat(response.getContent())
                  .contains("/assets Index File");

        // Subdirectory listing:
        request.setURI(DUMMY_SERVLET + "some_directory");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(200);
        Assertions.assertThat(response.getContent())
                  .contains("/assets/some_directory Index File");

        // Subdirectory listing with slash:
        request.setURI(DUMMY_SERVLET + "some_directory/");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(200);
        Assertions.assertThat(response.getContent())
                  .contains("/assets/some_directory Index File");
    }

    @Test
    public void throwsA404IfNoIndexFileIsDefined() throws Exception {
        // Root directory listing:
        request.setURI(NOINDEX_SERVLET + '/');
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(404);

        // Subdirectory listing:
        request.setURI(NOINDEX_SERVLET + "some_directory");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(404);

        // Subdirectory listing with slash:
        request.setURI(NOINDEX_SERVLET + "some_directory/");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(404);
    }

    @Test
    public void doesNotAllowOverridingUrls() throws Exception {
        request.setURI(DUMMY_SERVLET + "file:/etc/passwd");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(404);
    }

    @Test
    public void doesNotAllowOverridingPaths() throws Exception {
        request.setURI(DUMMY_SERVLET + "/etc/passwd");
        response = HttpTester.parseResponse(servletTester.getResponses(request.generate()));
        Assertions.assertThat(response.getStatus())
                  .isEqualTo(404);
    }
}
