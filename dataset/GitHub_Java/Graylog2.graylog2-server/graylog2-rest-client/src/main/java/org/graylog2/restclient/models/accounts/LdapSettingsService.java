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
package org.graylog2.restclient.models.accounts;

import org.graylog2.restclient.lib.APIException;
import org.graylog2.restclient.lib.ApiClient;
import org.graylog2.restclient.models.api.requests.accounts.LdapSettingsRequest;
import org.graylog2.restclient.models.api.requests.accounts.LdapTestConnectionRequest;
import org.graylog2.restclient.models.api.responses.accounts.LdapConnectionTestResponse;
import org.graylog2.restclient.models.api.responses.accounts.LdapSettingsResponse;
import org.graylog2.restroutes.generated.routes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Singleton
public class LdapSettingsService {
    private static final Logger log = LoggerFactory.getLogger(LdapSettingsService.class);

    @Inject
    private ApiClient api;

    @Inject
    private LdapSettings.Factory ldapSettingsFactory;

    public LdapSettings load() {
        LdapSettingsResponse response;
        try {
            response = api.path(routes.LdapResource().getLdapSettings(), LdapSettingsResponse.class).execute();
        } catch (APIException e) {
            log.error("Unable to load LDAP settings.", e);
            return null;
        } catch (IOException e) {
            log.error("Unable to load LDAP settings.", e);
            return null;
        }
        final LdapSettings ldapSettings = ldapSettingsFactory.fromSettingsResponse(response);
        return ldapSettings;
    }

    public LdapSettings create(LdapSettingsRequest request) {
        if (!request.enabled) {
            // the other fields will be "disabled" in the form, thus all values will be null.
            // load the old settings, and set "enabled" to false in the response.
            final LdapSettings ldapSettings = load();
            ldapSettings.setEnabled(request.enabled);
            return ldapSettings;
        }
        // otherwise just create the new settings object.
        return ldapSettingsFactory.fromSettingsRequest(request);
    }

    public Set<String> loadGroups() {
        try {
            final Set<String> ldapGroups;
            //noinspection unchecked
            ldapGroups = (Set<String>)api.path(routes.LdapResource().readGroups(), Set.class).execute();
            return ldapGroups;
        } catch (IOException | APIException e) {
            log.error("Unable to load ldap groups", e);
            return Collections.emptySet();
        }
    }

    public Map<String, String> getGroupMapping() {
        try {
            //noinspection unchecked
            return (Map<String, String>)api.path(routes.LdapResource().readGroupMapping(), Map.class).execute();
        } catch (APIException | IOException e) {
            log.error("Unable to load ldap group mapping", e);
            return Collections.emptyMap();
        }
    }

    public void updateGroupMapping(Map<String, String> mapping) {
        try {
            api.path(routes.LdapResource().updateGroupMappingSettings()).body(mapping).execute();
        } catch (APIException | IOException e) {
            log.error("Unable to update ldap group mapping", e);
        }
    }

    public LdapConnectionTestResponse testLdapConfiguration(LdapTestConnectionRequest request) throws APIException, IOException {
        return api.path(routes.LdapResource().testLdapConfiguration(), LdapConnectionTestResponse.class).body(request).execute();
    }
}
