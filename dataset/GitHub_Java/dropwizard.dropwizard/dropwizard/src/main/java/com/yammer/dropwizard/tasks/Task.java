package com.yammer.dropwizard.tasks;

import com.google.common.collect.ImmutableMultimap;

import java.io.PrintWriter;

// TODO: 10/12/11 <coda> -- write tests for Task
// TODO: 10/12/11 <coda> -- write docs for Task

/**
 * An arbitrary administrative task which can be performed via the internal service interface.
 */
public abstract class Task {
    private final String name;

    /**
     * Create a new task with the given name.
     *
     * @param name the task's name
     */
    protected Task(String name) {
        this.name = name;
    }

    /**
     * Returns the task's name,
     *
     * @return the task's name
     */
    public String getName() {
        return name;
    }

    /**
     * Executes the task.
     *
     * @param parameters the query string parameters
     * @param output     a {@link PrintWriter} wrapping the output stream of the task
     * @throws Exception if something goes wrong
     */
    public abstract void execute(ImmutableMultimap<String, String> parameters,
                                 PrintWriter output) throws Exception;
}
