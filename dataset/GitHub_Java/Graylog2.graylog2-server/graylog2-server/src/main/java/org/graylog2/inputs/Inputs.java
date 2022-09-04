/**
 * Copyright 2013 Lennart Koopmann <lennart@torch.sh>
 *
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
 *
 */
package org.graylog2.inputs;


import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mongodb.BasicDBObject;
import org.bson.types.ObjectId;
import org.graylog2.Core;
import org.graylog2.plugin.configuration.Configuration;
import org.graylog2.plugin.configuration.ConfigurationException;
import org.graylog2.plugin.inputs.Extractor;
import org.graylog2.plugin.inputs.MessageInput;
import org.graylog2.plugin.inputs.MisfireException;
import org.graylog2.system.activities.Activity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class Inputs {

    private static final Logger LOG = LoggerFactory.getLogger(Inputs.class);

    private final Core core;
    private Map<String, MessageInput> runningInputs;
    private Map<String, String> availableInputs;

    private ExecutorService executor = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("systemjob-executor-%d").build()
    );

    public Inputs(Core core) {
        this.core = core;
        runningInputs = Maps.newHashMap();
        availableInputs = Maps.newHashMap();
    }

    public String launch(final MessageInput input, String id) {
        input.setId(id);
        runningInputs.put(id, input);

        executor.submit(new Runnable() {
            @Override
            public void run() {
                LOG.info("Starting [{}] input with ID <{}>", input.getClass().getCanonicalName(), input.getId());
                try {
                    input.launch();
                } catch (MisfireException e) {
                    String msg = "The [" + input.getClass().getCanonicalName() + "] input with ID <" + input.getId() + "> " +
                            "was accepted but misfired. Reason: " + e.getMessage();
                    core.getActivityWriter().write(new Activity(msg, Inputs.class));
                    LOG.error(msg, e);

                    // Clean up.
                    cleanInput(input);
                }
            }
        });

        return id;
    }

    public String launch(final MessageInput input) {
        return launch(input, UUID.randomUUID().toString());
    }

    public Map<String, MessageInput> getRunningInputs() {
        return runningInputs;
    }

    public boolean hasTypeRunning(Class klazz) {
        for (MessageInput input : runningInputs.values()) {
            if (input.getClass().equals(klazz)) {
                return true;
            }
        }

        return false;
    }

    public Map<String, String> getAvailableInputs() {
        return availableInputs;
    }

    public int runningCount() {
        return runningInputs.size();
    }

    public static MessageInput factory(String type) throws NoSuchInputTypeException {
        try {
            Class c = Class.forName(type);
            return (MessageInput) c.newInstance();
        } catch (ClassNotFoundException e) {
             throw new NoSuchInputTypeException("There is no input of type <" + type + "> registered.");
        } catch (Exception e) {
            throw new RuntimeException("Could not create input of type <" + type + ">", e);
        }
    }

    public void register(Class clazz, String name) {
        availableInputs.put(clazz.getCanonicalName(), name);
    }

    public void cleanInput(MessageInput input) {
        // Remove from running list.
        getRunningInputs().remove(input.getId());

        // Remove in Mongo.
        Input.destroy(new BasicDBObject("_id", new ObjectId(input.getPersistId())), core, Input.COLLECTION);
    }

    public void launchPersisted() {
        for (Input io : Input.allOfThisNode(core)) {
            MessageInput input = null;
            try {
                input = Inputs.factory(io.getType());
                input.configure(new Configuration(io.getConfiguration()), core);
                input.setTitle(io.getTitle());
                input.setCreatorUserId(io.getCreatorUserId());
                input.setPersistId(io.getId().toStringMongod());
                input.setCreatedAt(io.getCreatedAt());

                for (Extractor extractor : io.getExtractors()) {
                    input.addExtractor(extractor.getId(), extractor);
                }
            } catch (NoSuchInputTypeException e) {
                LOG.warn("Cannot launch persisted input. No such type [{}].", io.getType());
                throw new WebApplicationException(e, Response.Status.NOT_FOUND);
            } catch (ConfigurationException e) {
                LOG.error("Missing or invalid input configuration.", e);
                throw new WebApplicationException(e, Response.Status.BAD_REQUEST);
            }

            launch(input, io.getInputId());
        }
    }

}
