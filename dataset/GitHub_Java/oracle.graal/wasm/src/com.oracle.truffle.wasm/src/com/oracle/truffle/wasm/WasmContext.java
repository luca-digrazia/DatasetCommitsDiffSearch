/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.wasm.predefined.PredefinedModule;

public final class WasmContext {
    private final Env env;
    private final WasmLanguage language;
    private final Memories memories;
    private final Globals globals;
    private final Tables tables;
    private final Linker linker;
    private Map<String, WasmModule> modules;

    public static WasmContext getCurrent() {
        return WasmLanguage.getCurrentContext();
    }

    public WasmContext(Env env, WasmLanguage language) {
        this.env = env;
        this.language = language;
        this.globals = new Globals();
        this.tables = new Tables();
        this.memories = new Memories();
        this.modules = new HashMap<>();
        this.linker = new Linker(language);
        initializePredefinedModules();
    }

    public CallTarget parse(Source source) {
        // TODO: Not used -- can we remove this?
        return env.parsePublic(source);
    }

    public WasmLanguage language() {
        return language;
    }

    public Memories memories() {
        return memories;
    }

    public Globals globals() {
        return globals;
    }

    public Tables tables() {
        return tables;
    }

    public Linker linker() {
        return linker;
    }

    public Iterable<Scope> getTopScopes() {
        // Go through all WasmModules parsed with this context, and create a Scope for each of them.
        ArrayList<Scope> scopes = new ArrayList<>();
        for (Map.Entry<String, WasmModule> entry : modules.entrySet()) {
            Scope scope = Scope.newBuilder(entry.getKey(), entry.getValue()).build();
            scopes.add(scope);
        }
        return scopes;
    }

    /**
     * Returns the map with all the modules that have been parsed.
     */
    public Map<String, WasmModule> modules() {
        return modules;
    }

    void registerModule(WasmModule module) {
        modules.put(module.name(), module);
    }

    private void initializePredefinedModules() {
        final String extraModuleValue = WasmOptions.PredefinedModules.getValue(env.getOptions());
        if (extraModuleValue.equals("")) {
            return;
        }
        final String[] moduleSpecs = extraModuleValue.split(",");
        for (String moduleSpec : moduleSpecs) {
            final String[] parts = moduleSpec.split(":");
            final String name = parts[0];
            final String key = parts[1];
            final WasmModule module = PredefinedModule.createPredefined(language, this, name, key);
            modules.put(name, module);
        }
    }
}
