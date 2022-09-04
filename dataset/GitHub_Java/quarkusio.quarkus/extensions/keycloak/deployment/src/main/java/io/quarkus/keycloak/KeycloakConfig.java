package io.quarkus.keycloak;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.keycloak.representations.adapters.config.PolicyEnforcerConfig;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.quarkus.runtime.annotations.DefaultConverter;

@ConfigRoot
public final class KeycloakConfig {

    /**
     * Name of the realm.
     */
    @ConfigItem
    String realm;

    /**
     * Name of the realm key.
     */
    @ConfigItem
    Optional<String> realmPublicKey;

    /**
     * The client-id of the application. Each application has a client-id that is used to identify the application
     */
    @ConfigItem
    Optional<String> resource;

    /**
     * The base URL of the Keycloak server. All other Keycloak pages and REST service endpoints are derived from this.
     * It is usually of the form https://host:port/auth
     */
    @ConfigItem
    String authServerUrl;

    /**
     * Ensures that all communication to and from the Keycloak server is over HTTPS. In production this should be set to all.
     * This is OPTIONAL. The default value is external meaning that HTTPS is required by default for external requests.
     * Valid values are 'all', 'external' and 'none'
     */
    @ConfigItem(defaultValue = "external")
    String sslRequired;

    /**
     * The confidential port used by the Keycloak server for secure connections over SSL/TLS
     */
    @ConfigItem(defaultValue = "8443")
    int confidentialPort;

    /**
     * If set to true, the adapter will look inside the token for application level role mappings for the user.
     * If false, it will look at the realm level for user role mappings
     */
    @ConfigItem
    boolean useResourceRoleMappings;

    /**
     * This enables CORS support. It will handle CORS preflight requests. It will also look into the access token to
     * determine valid origins
     */
    @ConfigItem
    boolean enableCors;

    /**
     * If CORS is enabled, this sets the value of the Access-Control-Max-Age header. This is OPTIONAL. If not set,
     * this header is not returned in CORS responses
     */
    @ConfigItem(defaultValue = "-1")
    int corsMaxAge;

    /**
     * If CORS is enabled, this sets the value of the Access-Control-Allow-Headers header. This should be a comma-separated
     * string
     */
    @ConfigItem
    Optional<String> corsAllowedHeaders;

    /**
     * If CORS is enabled, this sets the value of the Access-Control-Allow-Methods header. This should be a comma-separated
     * string
     */
    @ConfigItem
    Optional<String> corsAllowedMethods;

    /**
     * If CORS is enabled, this sets the value of the Access-Control-Expose-Headers header. This should be a comma-separated
     * string
     */
    @ConfigItem
    Optional<String> corsExposedHeaders;

    /**
     * This should be set to true for services. If enabled the adapter will not attempt to authenticate users,
     * but only verify bearer tokens
     */
    @ConfigItem(defaultValue = "true")
    boolean bearerOnly;

    /**
     * This should be set to true if your application serves both a web application and web services (e.g. SOAP or REST).
     * It allows you to redirect unauthenticated users of the web application to the Keycloak login page, but send an HTTP 401
     * status code to unauthenticated SOAP or REST clients instead as they would not understand a redirect to the login page.
     * Keycloak auto-detects SOAP or REST clients based on typical headers like X-Requested-With, SOAPAction or Accept
     */
    @ConfigItem
    boolean autodetectBearerOnly;

    /**
     * If this application is a public client
     */
    @ConfigItem
    boolean publicClient;

    /**
     * Specify the credentials of the application. This is an object notation where the key is the credential type and the
     * value is the value of the credential type. Currently password and jwt is supported
     */
    @ConfigItem
    KeycloakConfigCredentials credentials;

    /**
     * If the Keycloak server requires HTTPS and this config option is set to true the Keycloak server’s certificate is
     * validated via the truststore, but host name validation is not done. This setting should only be used during development
     * and never in production as it will disable verification of SSL certificates. This setting may be useful in test
     * environments
     */
    @ConfigItem
    boolean allowAnyHostname;

    /**
     * If the Keycloak server requires HTTPS and this config option is set to true you do not have to specify a truststore.
     * This setting should only be used during development and never in production as it will disable verification
     * of SSL certificates
     */
    @ConfigItem
    boolean disableTrustManager;

    /**
     * If the adapter should refresh the access token for each request
     */
    @ConfigItem
    boolean alwaysRefreshToken;

    /**
     * The value is the file path to a keystore file. If you prefix the path with classpath:, then the truststore will be
     * obtained from the deployment’s classpath instead. Used for outgoing HTTPS communications to the Keycloak server
     */
    @ConfigItem
    Optional<String> truststore;

    /**
     * Password for the truststore keystore
     */
    @ConfigItem
    String truststorePassword;

    /**
     * This is the file path to a keystore file. This keystore contains client certificate for two-way SSL when the adapter
     * makes HTTPS requests to the Keycloak server
     */
    @ConfigItem
    Optional<String> clientKeystore;

    /**
     * Password for the client keystore
     */
    @ConfigItem
    String clientKeystorePassword;

    /**
     * Password for the client’s key
     */
    @ConfigItem
    String clientKeyPassword;

    /**
     * Adapters will make separate HTTP invocations to the Keycloak server to turn an access code into an access token.
     * This config option defines how many connections to the Keycloak server should be pooled
     */
    @ConfigItem(defaultValue = "20")
    int connectionPoolSize;

    /**
     * If true, then adapter will send registration request to Keycloak. It’s false by default and useful only when application
     * is clustered
     */
    @ConfigItem
    boolean registerNodeAtStartup;

    /**
     * Period for re-registration adapter to Keycloak. Useful when application is clustered
     */
    @ConfigItem(defaultValue = "-1")
    int registerNodePeriod;

    /**
     * Possible values are session and cookie. Default is session, which means that adapter stores account info in HTTP Session.
     * Alternative cookie means storage of info in cookie
     */
    @ConfigItem
    Optional<String> tokenStore;

    /**
     * When using a cookie store, this option sets the path of the cookie used to store account info. If it’s a relative path,
     * then it is assumed that the application is running in a context root, and is interpreted relative to that context root.
     * If it’s an absolute path, then the absolute path is used to set the cookie path. Defaults to use paths relative to the
     * context root
     */
    @ConfigItem
    Optional<String> adapterStateCookiePath;

    /**
     * OpenID Connect ID Token attribute to populate the UserPrincipal name with. If token attribute is null. Possible values
     * are sub, preferred_username, email, name, nickname, given_name, family_name
     */
    @ConfigItem(defaultValue = "sub")
    String principalAttribute;

    /**
     * The session id is changed by default on a successful login on some platforms to plug a security attack vector.
     * Change this to true if you want to turn this off
     */
    @ConfigItem
    boolean turnOffChangeSessionIdOnLogin;

    /**
     * Amount of time, in seconds, to preemptively refresh an active access token with the Keycloak server before it expires.
     * This is especially useful when the access token is sent to another REST client where it could expire before being
     * evaluated. This value should never exceed the realm’s access token lifespan
     */
    @ConfigItem
    int tokenMinimumTimeToLive;

    /**
     * Amount of time, in seconds, specifying minimum interval between two requests to Keycloak to retrieve new public keys.
     * It is 10 seconds by default. Adapter will always try to download new public key when it recognize token with unknown kid.
     * However it won’t try it more than once per 10 seconds (by default). This is to avoid DoS when attacker sends lots of
     * tokens with bad kid forcing adapter to send lots of requests to Keycloak
     */
    @ConfigItem(defaultValue = "10")
    int minTimeBetweenJwksRequests;

    /**
     * Amount of time, in seconds, specifying maximum interval between two requests to Keycloak to retrieve new public keys.
     * It is 86400 seconds (1 day) by default. Adapter will always try to download new public key when it recognize token
     * with unknown kid . If it recognize token with known kid, it will just use the public key downloaded previously.
     * However at least once per this configured interval (1 day by default) will be new public key always downloaded even if
     * the kid of token is already known
     */
    @ConfigItem(defaultValue = "86400")
    int publicKeyCacheTtl;

    /**
     * If set to true, then during authentication with the bearer token, the adapter will verify whether the token contains
     * this client name (resource) as an audience. The option is especially useful for services, which primarily serve
     * requests authenticated by the bearer token. This is set to false by default, however for improved security, it is
     * recommended to enable this. See Audience Support for more details about audience support
     */
    @ConfigItem
    boolean verifyTokenAudience;

    /**
     * If set to true will turn off processing of the access_token query parameter for bearer token processing.
     * Users will not be able to authenticate if they only pass in an access_token
     */
    @ConfigItem(name = "ignore-oauth-query-parameter")
    boolean ignoreOAuthQueryParameter;

    /**
     * The proxy url to use for requests to the auth-server.
     */
    @ConfigItem
    Optional<String> proxyUrl;

    /**
     * If needed, specify the Redirect URI rewrite rule. This is an object notation where the key is the regular expression to
     * which the Redirect URI is to be matched and the value is the replacement String. $ character can be used for
     * backreferences in the replacement String
     */
    @ConfigItem
    Map<String, String> redirectRewriteRules;

    /**
     * Policy enforcement configuration when using Keycloak Authorization Services
     */
    @ConfigItem
    KeycloakConfigPolicyEnforcer policyEnforcer;

    @ConfigGroup
    public static class KeycloakConfigCredentials {

        /**
         * The client secret
         */
        @ConfigItem
        Optional<String> secret;

        /**
         * The settings for client authentication with signed JWT
         */
        @ConfigItem
        Map<String, String> jwt;

        /**
         * The settings for client authentication with JWT using client secret
         */
        @ConfigItem
        Map<String, String> secretJwt;
    }

    @ConfigGroup
    public static class KeycloakConfigPolicyEnforcer {

        /**
         * Specifies how policies are enforced.
         */
        @ConfigItem
        boolean enable;

        /**
         * Specifies how policies are enforced.
         */
        @ConfigItem(defaultValue = "ENFORCING")
        String enforcementMode;

        /**
         * Specifies the paths to protect.
         */
        @ConfigItem
        Map<String, PathConfig> paths;

        /**
         * Defines how the policy enforcer should track associations between paths in your application and resources defined in
         * Keycloak.
         * The cache is needed to avoid unnecessary requests to a Keycloak server by caching associations between paths and
         * protected resources
         */
        @ConfigItem
        PathCacheConfig pathCache;

        /**
         * Specifies how the adapter should fetch the server for resources associated with paths in your application. If true,
         * the
         * policy
         * enforcer is going to fetch resources on-demand accordingly with the path being requested
         */
        @ConfigItem(defaultValue = "true")
        boolean lazyLoadPaths;

        /**
         * Defines a URL where a client request is redirected when an "access denied" message is obtained from the server.
         * By default, the adapter responds with a 403 HTTP status code
         */
        @ConfigItem
        Optional<String> onDenyRedirectTo;

        /**
         * Specifies that the adapter uses the UMA protocol.
         */
        @ConfigItem
        boolean userManagedAccess;

        /**
         * Defines a set of one or more claims that must be resolved and pushed to the Keycloak server in order to make these
         * claims available to policies
         */
        @ConfigItem
        ClaimInformationPointConfig claimInformationPoint;

        /**
         * Specifies how scopes should be mapped to HTTP methods. If set to true, the policy enforcer will use the HTTP method
         * from
         * the current request to check whether or not access should be granted
         */
        @ConfigItem
        boolean httpMethodAsScope;

        @ConfigGroup
        public static class PathConfig {

            /**
             * The name of a resource on the server that is to be associated with a given path
             */
            @ConfigItem
            Optional<String> name;

            /**
             * A URI relative to the application’s context path that should be protected by the policy enforcer
             */
            @ConfigItem
            Optional<String> path;

            /**
             * The HTTP methods (for example, GET, POST, PATCH) to protect and how they are associated with the scopes for a
             * given
             * resource in the server
             */
            @ConfigItem
            Map<String, MethodConfig> methods;

            /**
             * Specifies how policies are enforced
             */
            @DefaultConverter
            @ConfigItem(defaultValue = "ENFORCING")
            PolicyEnforcerConfig.EnforcementMode enforcementMode;

            /**
             * Defines a set of one or more claims that must be resolved and pushed to the Keycloak server in order to make
             * these
             * claims available to policies
             */
            @ConfigItem
            ClaimInformationPointConfig claimInformationPoint;
        }

        @ConfigGroup
        public static class MethodConfig {

            /**
             * The name of the HTTP method
             */
            @ConfigItem
            String method;

            /**
             * An array of strings with the scopes associated with the method
             */
            @ConfigItem
            List<String> scopes;

            /**
             * A string referencing the enforcement mode for the scopes associated with a method
             */
            @DefaultConverter
            @ConfigItem(defaultValue = "ALL")
            PolicyEnforcerConfig.ScopeEnforcementMode scopesEnforcementMode;
        }

        @ConfigGroup
        public static class PathCacheConfig {

            /**
             * Defines the time in milliseconds when the entry should be expired
             */
            @ConfigItem(defaultValue = "1000")
            int maxEntries = 1000;

            /**
             * Defines the limit of entries that should be kept in the cache
             */
            @ConfigItem(defaultValue = "30000")
            long lifespan = 30000;
        }

        @ConfigGroup
        public static class ClaimInformationPointConfig {

            /**
             *
             */
            @ConfigItem(name = ConfigItem.PARENT)
            Map<String, Map<String, Map<String, String>>> complexConfig;

            /**
             *
             */
            @ConfigItem(name = ConfigItem.PARENT)
            Map<String, Map<String, String>> simpleConfig;
        }
    }
}
