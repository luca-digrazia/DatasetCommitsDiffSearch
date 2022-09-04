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
package org.graylog.security.idp.provider;

import org.graylog.security.idp.IDPAuthCredentials;
import org.graylog2.plugin.database.users.User;
import org.graylog2.plugin.security.PasswordAlgorithm;
import org.graylog2.security.PasswordAlgorithmFactory;
import org.graylog2.shared.users.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;

public class MongoDBIdentityProvider {
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBIdentityProvider.class);

    private final UserService userService;
    private final PasswordAlgorithmFactory passwordAlgorithmFactory;

    @Inject
    public MongoDBIdentityProvider(UserService userService, PasswordAlgorithmFactory passwordAlgorithmFactory) {
        this.userService = userService;
        this.passwordAlgorithmFactory = passwordAlgorithmFactory;
    }

    public Optional<String> authenticate(IDPAuthCredentials authCredentials) {
        final String username = authCredentials.username();

        LOG.info("Trying to load user <{}> from database", username);
        final User user = userService.load(username);
        if (user == null) {
            LOG.warn("User <{}> not found in database", username);
            return Optional.empty();
        }
        if (user.isLocalAdmin()) {
            throw new IllegalStateException("Local admin user should have been handled earlier and not reach the IdP authenticator");
        }

        if (isValidPassword(user, authCredentials.password())) {
            LOG.info("Validating password for user <{}> succeeded", username);
            return Optional.of(user.getName());
        }
        LOG.warn("Validating password for user <{}> failed", username);
        return Optional.empty();
    }

    private boolean isValidPassword(User user, String password) {
        final PasswordAlgorithm passwordAlgorithm = passwordAlgorithmFactory.forPassword(user.getHashedPassword());
        if (passwordAlgorithm == null) {
            return false;
        }
        return passwordAlgorithm.matches(user.getHashedPassword(), password);
    }

    public String providerId() {
        return "000000000000000000000001";
    }

    public String providerTitle() {
        return "Internal MongoDB";
    }
}
