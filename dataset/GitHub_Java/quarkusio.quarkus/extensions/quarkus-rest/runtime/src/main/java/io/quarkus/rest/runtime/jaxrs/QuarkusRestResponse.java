package io.quarkus.rest.runtime.jaxrs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Link.Builder;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import io.quarkus.rest.runtime.client.QuarkusRestClientResponse;
import io.quarkus.rest.runtime.headers.HeaderUtil;
import io.quarkus.rest.runtime.headers.LinkHeaders;
import io.quarkus.rest.runtime.util.CaseInsensitiveMap;

/**
 * This is the Response class for user-created responses. The client response
 * object has more deserialising powers in @{link {@link QuarkusRestClientResponse}.
 */
public class QuarkusRestResponse extends Response {

    int status;
    String reasonPhrase;
    protected Object entity;
    MultivaluedMap<String, Object> headers;
    InputStream entityStream;
    private QuarkusRestStatusType statusType;
    private MultivaluedMap<String, String> stringHeaders;
    Annotation[] entityAnnotations;
    protected boolean consumed;
    protected boolean closed;
    protected boolean buffered;

    @Override
    public int getStatus() {
        return status;
    }

    /**
     * Internal: this is just cheaper than duplicating the response just to change the status
     */
    public void setStatus(int status) {
        this.status = status;
        statusType = null;
    }

    @Override
    public StatusType getStatusInfo() {
        if (statusType == null) {
            statusType = new QuarkusRestStatusType(status, reasonPhrase);
        }
        return statusType;
    }

    /**
     * Internal: this is just cheaper than duplicating the response just to change the status
     */
    public void setStatusInfo(StatusType statusType) {
        this.statusType = QuarkusRestStatusType.valueOf(statusType);
        status = statusType.getStatusCode();
    }

    @Override
    public Object getEntity() {
        // The spec says that getEntity() can be called after readEntity() to obtain the same entity,
        // but it also sort-of implies that readEntity() calls Reponse.close(), and the TCK does check
        // that we throw if closed and non-buffered
        checkClosed();
        return entity;
    }

    protected void setEntity(Object entity) {
        this.entity = entity;
        if (entity instanceof InputStream) {
            this.entityStream = (InputStream) entity;
        }
    }

    public InputStream getEntityStream() {
        return entityStream;
    }

    public void setEntityStream(InputStream entityStream) {
        this.entityStream = entityStream;
    }

    protected <T> T readEntity(Class<T> entityType, Type genericType, Annotation[] annotations) {
        // TODO: we probably need better state handling
        if (entity != null && entityType.isInstance(entity)) {
            // Note that this works if entityType is InputStream where we return it without closing it, as per spec
            return (T) entity;
        }
        checkClosed();
        // Spec says to throw this
        throw new ProcessingException(
                "Request could not be mapped to type " + (genericType != null ? genericType : entityType));
    }

    @Override
    public <T> T readEntity(Class<T> entityType) {
        return readEntity(entityType, entityType, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readEntity(GenericType<T> entityType) {
        return (T) readEntity(entityType.getRawType(), entityType.getType(), null);
    }

    @Override
    public <T> T readEntity(Class<T> entityType, Annotation[] annotations) {
        return readEntity(entityType, entityType, annotations);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T readEntity(GenericType<T> entityType, Annotation[] annotations) {
        return (T) readEntity(entityType.getRawType(), entityType.getType(), annotations);
    }

    @Override
    public boolean hasEntity() {
        // The TCK checks that
        checkClosed();
        // we have an entity already read, or still to be read
        return entity != null || entityStream != null;
    }

    @Override
    public boolean bufferEntity() {
        checkClosed();
        // must be idempotent
        if (buffered) {
            return true;
        }
        if (entityStream != null && !consumed) {
            // let's not try this again, even if it fails
            consumed = true;
            // we're supposed to read the entire stream, but if we can rewind it there's no point so let's keep it
            if (!entityStream.markSupported()) {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                try {
                    while ((read = entityStream.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                    entityStream.close();
                } catch (IOException x) {
                    throw new UncheckedIOException(x);
                }
                entityStream = new ByteArrayInputStream(os.toByteArray());
            }
            buffered = true;
            return true;
        }
        return false;
    }

    protected void checkClosed() {
        // apparently the TCK says that buffered responses don't care about being closed
        if (closed && !buffered)
            throw new IllegalStateException("Response has been closed");
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (entityStream != null) {
                try {
                    entityStream.close();
                } catch (IOException e) {
                    throw new ProcessingException(e);
                }
            }
        }
    }

    @Override
    public MediaType getMediaType() {
        return HeaderUtil.getMediaType(headers);
    }

    @Override
    public Locale getLanguage() {
        return HeaderUtil.getLanguage(headers);
    }

    @Override
    public int getLength() {
        return HeaderUtil.getLength(headers);
    }

    @Override
    public Set<String> getAllowedMethods() {
        return HeaderUtil.getAllowedMethods(headers);
    }

    @Override
    public Map<String, NewCookie> getCookies() {
        return HeaderUtil.getNewCookies(headers);
    }

    @Override
    public EntityTag getEntityTag() {
        return HeaderUtil.getEntityTag(headers);
    }

    @Override
    public Date getDate() {
        return HeaderUtil.getDate(headers);
    }

    @Override
    public Date getLastModified() {
        return HeaderUtil.getLastModified(headers);
    }

    @Override
    public URI getLocation() {
        return HeaderUtil.getLocation(headers);
    }

    private LinkHeaders getLinkHeaders() {
        return new LinkHeaders(headers);
    }

    @Override
    public Set<Link> getLinks() {
        return new HashSet<>(getLinkHeaders().getLinks());
    }

    @Override
    public boolean hasLink(String relation) {
        return getLinkHeaders().getLinkByRelationship(relation) != null;
    }

    @Override
    public Link getLink(String relation) {
        return getLinkHeaders().getLinkByRelationship(relation);
    }

    @Override
    public Builder getLinkBuilder(String relation) {
        Link link = getLinkHeaders().getLinkByRelationship(relation);
        if (link == null) {
            return null;
        }
        return Link.fromLink(link);
    }

    @Override
    public MultivaluedMap<String, Object> getMetadata() {
        return headers;
    }

    @Override
    public MultivaluedMap<String, String> getStringHeaders() {
        // FIXME: is this mutable?
        if (stringHeaders == null) {
            // let's keep this map case-insensitive
            stringHeaders = new CaseInsensitiveMap<>();
            for (Entry<String, List<Object>> entry : headers.entrySet()) {
                List<String> stringValues = new ArrayList<>(entry.getValue().size());
                for (Object value : entry.getValue()) {
                    stringValues.add(HeaderUtil.headerToString(value));
                }
                stringHeaders.put(entry.getKey(), stringValues);
            }
        }

        return stringHeaders;
    }

    @Override
    public String getHeaderString(String name) {
        return HeaderUtil.getHeaderString(getStringHeaders(), name);
    }

    public Annotation[] getEntityAnnotations() {
        return entityAnnotations;
    }
}
