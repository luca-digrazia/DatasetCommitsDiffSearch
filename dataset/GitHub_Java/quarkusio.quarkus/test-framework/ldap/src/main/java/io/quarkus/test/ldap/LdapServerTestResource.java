package io.quarkus.test.ldap;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldif.LDIFReader;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class LdapServerTestResource implements QuarkusTestResourceLifecycleManager {

    protected InMemoryDirectoryServer ldapServer;

    public LdapServerTestResource() throws LDAPException {
        InMemoryListenerConfig listenerConfig = new InMemoryListenerConfig("listener", InetAddress.getLoopbackAddress(),
                10389, null, null, null);
        InMemoryDirectoryServerConfig inMemoryDirectoryServerConfig = new InMemoryDirectoryServerConfig("dc=quarkus,dc=io");
        inMemoryDirectoryServerConfig.setListenerConfigs(listenerConfig);
        inMemoryDirectoryServerConfig.addAdditionalBindCredentials("uid=admin,ou=system", "secret");
        ldapServer = new InMemoryDirectoryServer(inMemoryDirectoryServerConfig);
        ldapServer.importFromLDIF(true, new LDIFReader(ClassLoader.getSystemResourceAsStream("quarkus-io.ldif")));
    }

    @Override
    public Map<String, String> start() {
        try {
            ldapServer.startListening();
            System.out.println("[INFO] LDAP server started");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return Collections.emptyMap();
    }

    @Override
    public synchronized void stop() {
        if (ldapServer != null) {
            ldapServer.shutDown(false);
            System.out.println("[INFO] LDAP server was shut down");
            ldapServer = null;
        }
    }
}
