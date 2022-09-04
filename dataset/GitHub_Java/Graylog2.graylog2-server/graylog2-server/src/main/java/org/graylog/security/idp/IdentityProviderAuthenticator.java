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
package org.graylog.security.idp;

import org.graylog2.shared.users.UserService;

import javax.inject.Inject;
import java.util.Optional;

public class IdentityProviderAuthenticator {
    private final GlobalIdentityProviderConfig providerConfig;
    private final UserProfileProvisioner userProfileProvisioner;
    private final UserService userService;

    @Inject
    public IdentityProviderAuthenticator(GlobalIdentityProviderConfig providerConfig,
                                         UserProfileProvisioner userProfileProvisioner,
                                         UserService userService) {
        this.providerConfig = providerConfig;
        this.userProfileProvisioner = userProfileProvisioner;
        this.userService = userService;
    }

    /**
     * Tries to authenticate the username with the given password and returns the authenticated username if successful.
     *
     * @param authCredentials the authentication credentials
     * @return the authenticated username
     */
    public IDPAuthResult authenticate(IDPAuthCredentials authCredentials) {
        final Optional<IdentityProvider> activeProvider = providerConfig.getActiveProvider();

        if (activeProvider.isPresent()) {
            return authenticate(authCredentials, activeProvider.get());
        }
        return authenticate(authCredentials, providerConfig.getDefaultProvider());
    }

    private IDPAuthResult authenticate(IDPAuthCredentials authCredentials, IdentityProvider provider) {
        final Optional<UserProfile> userProfile = provider.authenticateAndProvision(authCredentials, userProfileProvisioner);

        if (userProfile.isPresent()) {
            return IDPAuthResult.builder()
                    .username(authCredentials.username())
                    //.userProfileId(userProfile.get().uid())
                    .userProfileId(userProfile.get().username()) // TODO: Switch to uid() once our session implementation can handle it
                    .providerId(provider.providerId())
                    .providerTitle(provider.providerTitle())
                    .build();
        }

        return failResult(authCredentials, provider);
    }

    private IDPAuthResult failResult(IDPAuthCredentials authCredentials, IdentityProvider provider) {
        return IDPAuthResult.failed(authCredentials.username(), provider.providerId(), provider.providerTitle());
    }
}
