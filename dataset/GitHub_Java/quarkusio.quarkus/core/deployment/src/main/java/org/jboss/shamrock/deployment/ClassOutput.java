package org.jboss.shamrock.deployment;

import java.io.IOException;

/**
 * Interface that represents a target for generated bytecode
 */
public interface ClassOutput {

    /**
     * Writes some generate bytecode to an output target
     *
     * @param className The class name
     * @param data      The bytecode bytes
     * @throws IOException If the class cannot be written
     */
    void writeClass(boolean applicationClass, String className, byte[] data) throws IOException;

    //TODO: we should not need both these classes
    static org.jboss.protean.gizmo.ClassOutput gizmoAdaptor(ClassOutput out, boolean applicationClass) {
        return new org.jboss.protean.gizmo.ClassOutput() {
            @Override
            public void write(String name, byte[] data) {
                try {
                    out.writeClass(applicationClass, name, data);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
