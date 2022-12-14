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
package org.graylog2.security.encryption;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedValueDeserializerTest {
    private ObjectMapper objectMapper;
    private EncryptedValueService encryptedValueService;

    @BeforeEach
    void setUp() {
        this.encryptedValueService = new EncryptedValueService("1234567890abcdef");

        final EncryptedValueDeserializer deser = new EncryptedValueDeserializer(encryptedValueService);
        final SimpleModule module = new SimpleModule("Test").addDeserializer(EncryptedValue.class, deser);

        this.objectMapper = new ObjectMapper().registerModule(module);
    }

    @Test
    void deserializeSetValue() throws Exception {
        final EncryptedValue value = objectMapper.readValue("{\"set_value\":\"password\"}", EncryptedValue.class);

        assertThat(value).isNotNull();
        assertThat(value.value()).isNotBlank();
        assertThat(value.salt()).isNotBlank();
    }

    @Test
    void deserializeStringAsPassword() throws Exception {
        final Map<String, EncryptedValue> map = objectMapper.readValue("{\"password\": \"mypass\"}", new TypeReference<Map<String, EncryptedValue>>() {});
        final EncryptedValue value = map.get("password");

        assertThat(value).isNotNull();
        assertThat(value.value()).isNotBlank();
        assertThat(value.salt()).isNotBlank();
    }

    @Test
    void deserializeNullValue() throws Exception {
        final EncryptedValue value = objectMapper.readValue("null", EncryptedValue.class);

        assertThat(value).isNotNull();
        assertThat(value.value()).isEmpty();
        assertThat(value.salt()).isEmpty();
    }

    @Test
    void deserializeSetValueWithInvalidValues() throws Exception {
        assertThatThrownBy(() -> objectMapper.readValue("{\"set_value\":\"\"}", EncryptedValue.class))
                .hasMessageContaining("set_value")
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"set_value\":\"        \"}", EncryptedValue.class))
                .hasMessageContaining("set_value")
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"set_value\":null}", EncryptedValue.class))
                .hasMessageContaining("set_value")
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"set_value\":1}", EncryptedValue.class))
                .hasMessageContaining("set_value")
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"set_value\":1.2}", EncryptedValue.class))
                .hasMessageContaining("set_value")
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"set_value\":true}", EncryptedValue.class))
                .hasMessageContaining("set_value")
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"set_value\":{}}", EncryptedValue.class))
                .hasMessageContaining("set_value")
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{\"set_value\":[]}", EncryptedValue.class))
                .hasMessageContaining("set_value")
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> objectMapper.readValue("{}", EncryptedValue.class))
                .hasMessageContaining("set_value")
                .isInstanceOf(JsonMappingException.class);
    }

    @Test
    void deserializeForDatabase() throws Exception {
        EncryptedValueMapperConfig.enableDatabase(objectMapper);
        final EncryptedValue value = objectMapper
                .readValue("{\"encrypted_value\":\"2d043f9a7d5a5a7537d3e93c93c5dc40\",\"salt\":\"c93c0263bfc3713d\"}", EncryptedValue.class);

        assertThat(value).isNotNull();
        assertThat(value.value()).isEqualTo("2d043f9a7d5a5a7537d3e93c93c5dc40");
        assertThat(value.salt()).isEqualTo("c93c0263bfc3713d");
        assertThat(encryptedValueService.decrypt(value)).isEqualTo("password");
    }

    @Test
    void deserializeUnsetForDatabase() throws Exception {
        EncryptedValueMapperConfig.enableDatabase(objectMapper);
        final EncryptedValue value = objectMapper
                .readValue("{\"encrypted_value\":\"\",\"salt\":\"\"}", EncryptedValue.class);

        assertThat(value).isNotNull();
        assertThat(value.value()).isEmpty();
        assertThat(value.salt()).isEmpty();
        assertThat(value.isSet()).isFalse();
    }

    @Test
    void deserializeForDatabaseAndInvalidValues() throws Exception {
        EncryptedValueMapperConfig.enableDatabase(objectMapper);

        assertThatThrownBy(() -> objectMapper
                .readValue("{\"salt\":\"\"}", EncryptedValue.class)
        ).isInstanceOf(JsonMappingException.class)
                .hasMessageContaining("encrypted_value and salt");

        assertThatThrownBy(() -> objectMapper
                .readValue("{\"encrypted_value\":\"\"}", EncryptedValue.class)
        ).isInstanceOf(JsonMappingException.class)
                .hasMessageContaining("encrypted_value and salt");

        assertThatThrownBy(() -> objectMapper
                .readValue("{}", EncryptedValue.class)
        ).isInstanceOf(JsonMappingException.class)
                .hasMessageContaining("encrypted_value and salt");
    }
}
