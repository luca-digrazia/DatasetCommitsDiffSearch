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
package org.graylog2;

import com.github.joschi.jadconfig.JadConfig;
import com.github.joschi.jadconfig.RepositoryException;
import com.github.joschi.jadconfig.ValidationException;
import com.github.joschi.jadconfig.repositories.InMemoryRepository;
import com.google.common.collect.Maps;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for {@link Configuration} class
 */
public class ConfigurationTest {

    Map<String, String> validProperties;
    private File tempFile;

    @BeforeClass
    public void setUp() {
        validProperties = Maps.newHashMap();

        try {
            tempFile = File.createTempFile("graylog", null);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // Required properties
        validProperties.put("password_secret", "ipNUnWxmBLCxTEzXcyamrdy0Q3G7HxdKsAvyg30R9SCof0JydiZFiA3dLSkRsbLF");
        validProperties.put("elasticsearch_config_file", tempFile.getAbsolutePath());
        validProperties.put("use_gelf", "true");
        validProperties.put("gelf_listen_port", "12201");
        validProperties.put("root_password_sha2", "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918"); // sha2 of admin

        // Additional numerical properties
        validProperties.put("amqp_port", "5672");
        validProperties.put("forwarder_loggly_timeout", "3");

        validProperties.put("retention_strategy", "delete");
    }

    @AfterClass
    public void tearDown() {
        tempFile.delete();
    }

    @Test
    public void testGetElasticSearchIndexPrefix() throws RepositoryException, ValidationException {

        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();

        //Assert.assertEquals("graylog2", configuration.getElasticSearchIndexPrefix());
    }

    /*@Test
    public void testGetAMQPSubscribedQueuesEmpty() throws RepositoryException, ValidationException {
        validProperties.put("amqp_subscribed_queues", "");
        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();

        Assert.assertNull(configuration.getAmqpSubscribedQueues());
    }

    @Test
    public void testGetAMQPSubscribedQueuesMalformed() throws RepositoryException, ValidationException {
        validProperties.put("amqp_subscribed_queues", "queue-invalid");
        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();

        Assert.assertNull(configuration.getAmqpSubscribedQueues());
    }

    @Test
    public void testGetAMQPSubscribedQueuesInvalidQueueType() throws RepositoryException, ValidationException {
        validProperties.put("amqp_subscribed_queues", "queue1:gelf,queue2:invalid");
        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();

        Assert.assertNull(configuration.getAmqpSubscribedQueues());
    }

    @Test
    public void testGetAMQPSubscribedQueues() throws RepositoryException, ValidationException {
        validProperties.put("amqp_subscribed_queues", "queue1:gelf,queue2:syslog");
        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();

        Assert.assertEquals(2, configuration.getAmqpSubscribedQueues().size());
    }*/

    @Test
    public void testDefaultMessageCacheSpoolDir() throws RepositoryException, ValidationException {
        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();

        Assert.assertEquals(configuration.getMessageCacheSpoolDir(), "spool", "Default message_cache_spool_dir is not 'spool'");
    }

    @Test
    public void testMessageCacheSpoolDir() throws RepositoryException, ValidationException {
        final HashMap<String, String> properties = Maps.newHashMap(validProperties);
        properties.put("message_cache_spool_dir", "wat?/a/spool/dir");

        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(properties), configuration).process();

        Assert.assertEquals(configuration.getMessageCacheSpoolDir(), "wat?/a/spool/dir");
    }

    @Test
    public void testDefaultMessageCacheCommitInterval() throws RepositoryException, ValidationException {
        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();

        Assert.assertEquals(configuration.getMessageCacheCommitInterval(), 1000, "Default message_cache_commit_interval is not '1000'");
    }

    @Test
    public void testMessageCacheCommitInterval() throws RepositoryException, ValidationException {
        final HashMap<String, String> properties = Maps.newHashMap(validProperties);
        properties.put("message_cache_commit_interval", "4000");

        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(properties), configuration).process();

        Assert.assertEquals(configuration.getMessageCacheCommitInterval(), 4000);
    }

    @Test
    public void testDefaultMessageCacheOffHeap() throws RepositoryException, ValidationException {
        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(validProperties), configuration).process();

        Assert.assertEquals(configuration.isMessageCacheOffHeap(), true, "Default message_cache_off_heap is not 'true'");
    }

    @Test
    public void testMessageCacheOffHeap() throws RepositoryException, ValidationException {
        final HashMap<String, String> properties = Maps.newHashMap(validProperties);
        properties.put("message_cache_off_heap", "false");

        Configuration configuration = new Configuration();
        new JadConfig(new InMemoryRepository(properties), configuration).process();

        Assert.assertEquals(configuration.isMessageCacheOffHeap(), false);
    }
}
