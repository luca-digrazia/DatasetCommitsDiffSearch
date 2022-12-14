package org.graalvm.polyglot.examples;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * This example shows how harmful long running scripts can be cancelled. Cancelling the current
 * execution will render the context inconsistent and therefore unusable.
 */
public class CancelExecution {

    public static void main(String[] args) throws InterruptedException {
        ExecutorService service = Executors.newFixedThreadPool(1);
        Engine engine = Engine.create();
        Context context = engine.getLanguage("js").createContext();

        // we submit a harmful infinite script to the executor
        Future<Value> future = service.submit(() -> context.eval("while(true)"));

        // wait some time to let the execution complete.
        Thread.sleep(1000);

        /*
         * closes the context and cancels the running execution. This can be done on any parallel
         * thread. Alternatively Engine.close(true) can be used to close all running contexts of an
         * engine.
         */
        context.close(true);

        try {
            future.get();
        } catch (ExecutionException e) {
            PolyglotException polyglotException = (PolyglotException) e.getCause();
            /*
             * After the execution got cancelled the executing thread stops by throwning a
             * PolyglotException with the cancelled flag set.
             */
            assert polyglotException.isCancelled();
        }
    }

}
