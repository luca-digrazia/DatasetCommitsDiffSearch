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
package org.graylog2.rest;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.graylog2.indexer.results.ResultMessage;
import org.graylog2.rest.resources.search.responses.SearchResponse;
import org.graylog2.shared.rest.AdditionalMediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

@Provider
@Produces(AdditionalMediaType.TEXT_CSV)
public class SearchResponseCsvWriter implements MessageBodyWriter<SearchResponse> {

    public static final MediaType TEXT_CSV = new MediaType("text", "csv");

    private static final Logger LOG = LoggerFactory.getLogger(SearchResponseCsvWriter.class);

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return SearchResponse.class.equals(type) && TEXT_CSV.isCompatible(mediaType);
    }

    @Override
    public long getSize(SearchResponse searchResponse, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(SearchResponse searchResponse, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException, WebApplicationException {
        final CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(entityStream, StandardCharsets.UTF_8));
        final ImmutableSortedSet<String> sortedFields = ImmutableSortedSet.copyOf(
                Iterables.concat(searchResponse.fields(), Lists.newArrayList("source", "timestamp")));

        // write field headers
        csvWriter.writeNext(sortedFields.toArray(new String[sortedFields.size()]));

        // write result set in same order as the header row
        final String[] fieldValues = new String[sortedFields.size()];
        for (ResultMessage message : searchResponse.messages()) {
            int idx = 0;
            // first collect all values from the current message
            for (String fieldName : sortedFields) {
                final Object val = message.getMessage().get(fieldName);
                fieldValues[idx++] = ((val == null) ? null : val.toString().replaceAll("\n", "\\\\n"));
                fieldValues[idx++] = ((val == null) ? null : val.toString().replaceAll("\r", "\\\\r"));
            }
            // write the complete line, some fields might not be present in the message, so there might be null values
            csvWriter.writeNext(fieldValues);
        }
        if (csvWriter.checkError()) {
            LOG.error("Encountered unspecified error when writing message result as CSV, result is likely malformed.");
        }
        csvWriter.close();
    }
}
