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
package org.graylog2.security.ldap;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.apache.shiro.codec.Hex;
import org.bson.types.ObjectId;
import org.graylog2.Configuration;
import org.graylog2.database.CollectionName;
import org.graylog2.database.NotFoundException;
import org.graylog2.database.PersistedImpl;
import org.graylog2.plugin.database.validators.Validator;
import org.graylog2.security.AESTools;
import org.graylog2.shared.security.ldap.LdapSettings;
import org.graylog2.shared.users.Role;
import org.graylog2.users.RoleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Map;

@CollectionName("ldap_settings")
public class LdapSettingsImpl extends PersistedImpl implements LdapSettings {

    public interface Factory {
        LdapSettingsImpl createEmpty();
        LdapSettingsImpl create(ObjectId objectId, Map<String, Object> fields);
    }

    private static final Logger LOG = LoggerFactory.getLogger(LdapSettingsImpl.class);

    public static final String ENABLED = "enabled";
    public static final String SYSTEM_USERNAME = "system_username";
    public static final String SYSTEM_PASSWORD = "system_password";
    public static final String SYSTEM_PASSWORD_SALT = "system_password_salt";
    public static final String LDAP_URI = "ldap_uri";
    public static final String SEARCH_PATTERN = "principal_search_pattern";
    public static final String SEARCH_BASE = "search_base";
    public static final String DISPLAY_NAME_ATTRIBUTE = "username_attribute";
    public static final String USE_START_TLS = "use_start_tls";
    public static final String ACTIVE_DIRECTORY = "active_directory";
    public static final String DEFAULT_GROUP = "reader";
    public static final String TRUST_ALL_CERTS = "trust_all_certificates";
    public static final String GROUP_MAPPING = "group_role_mapping";
    public static final String GROUP_SEARCH_BASE = "group_search_base";
    public static final String GROUP_ID_ATTRIBUTE = "group_id_attribute";

    protected Configuration configuration;
    private final RoleService roleService;

    @AssistedInject
    public LdapSettingsImpl(Configuration configuration, RoleService roleService) {
        super(Maps.<String, Object>newHashMap());
        this.configuration = configuration;
        this.roleService = roleService;
    }

    @AssistedInject
    public LdapSettingsImpl(Configuration configuration, RoleService roleService, @Assisted ObjectId id, @Assisted Map<String, Object> fields) {
        super(id, fields);
        this.configuration = configuration;
        this.roleService = roleService;
    }

    @Override
    public Map<String, Validator> getValidations() {
        return null;
    }

    @Override
    public Map<String, Validator> getEmbeddedValidations(String key) {
        return null;
    }

    @Override
    public String getSystemUserName() {
        final Object o = fields.get(SYSTEM_USERNAME);
        return o != null ? o.toString() : "";
    }

    @Override
    public void setSystemUsername(String systemUsername) {
        fields.put(SYSTEM_USERNAME, systemUsername);
    }

    @Override
    public String getSystemPassword() {
        final Object o = fields.get(SYSTEM_PASSWORD);
        if (o == null) return "";
        if (getSystemPasswordSalt().isEmpty()) {
            // this is an old version of the database that doesn't have the salt value,
            // simply return the password, because it's unencrypted.
            // The next time we will generate a salt and then re-use that value.
            // TODO remove this after 0.20 is out, and the RC versions are pulled.
            LOG.debug("Old database version does not have salted, encrypted password. Please save the LDAP settings again.");
            return o.toString();
        }
        String encryptedPw = o.toString();
        return AESTools.decrypt(
                encryptedPw,
                configuration.getPasswordSecret().substring(0, 16),
                getSystemPasswordSalt());
    }

    @Override
    public void setSystemPassword(String systemPassword) {
        // set new salt value, if we didn't have any.
        if (getSystemPasswordSalt().isEmpty()) {
            LOG.debug("Generating new salt for LDAP system password.");
            final SecureRandom random = new SecureRandom();
            byte[] saltBytes = new byte[8];
            random.nextBytes(saltBytes);
            setSystemPasswordSalt(Hex.encodeToString(saltBytes));
        }
        final String encrypted = AESTools.encrypt(
                systemPassword,
                configuration.getPasswordSecret().substring(0, 16),
                getSystemPasswordSalt());
        fields.put(SYSTEM_PASSWORD, encrypted);
    }

    @Override
    public String getSystemPasswordSalt() {
        Object o = fields.get(SYSTEM_PASSWORD_SALT);
        return (o!= null) ? o.toString() : "";
    }

    @Override
    public void setSystemPasswordSalt(String salt) {
        fields.put(SYSTEM_PASSWORD_SALT, salt);
    }

    @Override
    public URI getUri() {
        final Object o = fields.get(LDAP_URI);
        return o != null ? URI.create(o.toString()) : null;
    }

    @Override
    public void setUri(URI ldapUri) {
        fields.put(LDAP_URI, ldapUri.toString());
    }

    @Override
    public String getSearchBase() {
        final Object o = fields.get(SEARCH_BASE);
        return o != null ? o.toString() : "";
    }

    @Override
    public void setSearchBase(String searchBase) {
        fields.put(SEARCH_BASE, searchBase);
    }

    @Override
    public String getSearchPattern() {
        final Object o = fields.get(SEARCH_PATTERN);
        return o != null ? o.toString() : "";
    }

    @Override
    public void setSearchPattern(String searchPattern) {
        fields.put(SEARCH_PATTERN, searchPattern);
    }

    @Override
    public String getDisplayNameAttribute() {
        final Object o = fields.get(DISPLAY_NAME_ATTRIBUTE);
        return o != null ? o.toString() : "";
    }

    @Override
    public void setDisplayNameAttribute(String displayNameAttribute) {
        fields.put(DISPLAY_NAME_ATTRIBUTE, displayNameAttribute);
    }

    @Override
    public boolean isEnabled() {
        final Object o = fields.get(ENABLED);
        return o != null ? Boolean.valueOf(o.toString()) : false;
    }

    @Override
    public void setEnabled(boolean enabled) {
        fields.put(ENABLED, enabled);
    }

    @Override
    public void setUseStartTls(boolean useStartTls) {
        fields.put(USE_START_TLS, useStartTls);
    }

    @Override
    public boolean isUseStartTls() {
        final Object o = fields.get(USE_START_TLS);
        return o != null ? Boolean.valueOf(o.toString()) : false;
    }

    @Override
    public void setActiveDirectory(boolean activeDirectory) {
        fields.put(ACTIVE_DIRECTORY, activeDirectory);
    }

    @Override
    public boolean isActiveDirectory() {
        final Object o = fields.get(ACTIVE_DIRECTORY);
        return o != null ? Boolean.valueOf(o.toString()) : false;
    }

    @Override
    public String getDefaultGroup() {
        final Object o = fields.get(DEFAULT_GROUP);
        return o != null ? o.toString() : "reader"; // reader is the safe default
    }

    @Override
    public void setDefaultGroup(String defaultGroup) {
        fields.put(DEFAULT_GROUP, defaultGroup);
    }

    @Override
    public boolean isTrustAllCertificates() {
        final Object o = fields.get(TRUST_ALL_CERTS);
        return o != null ? Boolean.valueOf(o.toString()) : false;
    }

    @Override
    public void setTrustAllCertificates(boolean trustAllCertificates) {
        fields.put(TRUST_ALL_CERTS, trustAllCertificates);
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> getGroupMapping() {
        final Map<String, String> groupMapping = (Map<String, String>) fields.get(GROUP_MAPPING);

        if (groupMapping == null || groupMapping.isEmpty()) {
            return Maps.newHashMap();
        }
        else {
            // we store role ids, but the outside world uses role names to identify them
            try {
                final Map<String, Role> idToRole = roleService.loadAllIdMap();
                return Maps.newHashMap(Maps.transformValues(groupMapping, new Role.RoleIdToNameFunction(idToRole)));
            } catch (NotFoundException e) {
                LOG.error("Unable to load role mapping");
                return Maps.newHashMap();
            }
        }
    }

    @Override
    public void setGroupMapping(Map<String, String> mapping) {
        Map<String, String> internal;
        if (mapping == null) {
            internal = Maps.newHashMap();
        } else {
            // we store ids internally but external users use the group names
            try {
                final Map<String, Role> nameToRole = Maps.uniqueIndex(roleService.loadAll(), new Role.RoleToNameFunction());

                internal = Maps.newHashMap(Maps.transformValues(mapping, new Function<String, String>() {
                    @Nullable
                    @Override
                    public String apply(@Nullable String groupName) {
                        if (groupName == null || !nameToRole.containsKey(groupName)) {
                            return null;
                        }
                        return nameToRole.get(groupName).getId();
                    }
                }));
            } catch (NotFoundException e) {
                LOG.error("Unable to convert group names to ids", e);
                internal = Maps.newHashMap();
            }
        }

        fields.put(GROUP_MAPPING, internal);
    }

    @Override
    public String getGroupSearchBase() {
        final Object o = fields.get(GROUP_SEARCH_BASE);
        return o != null ? o.toString() : "";
    }

    @Override
    public void setGroupSearchBase(String groupSearchBase) {
        fields.put(GROUP_SEARCH_BASE, groupSearchBase);
    }

    @Override
    public String getGroupIdAttribute() {
        final Object o = fields.get(GROUP_ID_ATTRIBUTE);
        return o != null ? o.toString() : "";
    }

    @Override
    public void setGroupIdAttribute(String groupIdAttribute) {
        fields.put(GROUP_ID_ATTRIBUTE, groupIdAttribute);
    }

}
