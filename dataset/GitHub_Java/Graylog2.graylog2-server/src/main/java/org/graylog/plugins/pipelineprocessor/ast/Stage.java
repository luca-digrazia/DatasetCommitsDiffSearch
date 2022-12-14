/**
 * This file is part of Graylog Pipeline Processor.
 *
 * Graylog Pipeline Processor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog Pipeline Processor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog Pipeline Processor.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.plugins.pipelineprocessor.ast;

import com.google.auto.value.AutoValue;

import java.util.List;

@AutoValue
public abstract class Stage implements Comparable<Stage> {
    private List<Rule> rules;

    public abstract int stage();
    public abstract boolean matchAll();
    public abstract List<String> ruleReferences();

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static Builder builder() {
        return new AutoValue_Stage.Builder();
    }

    public abstract Builder toBuilder();

    @Override
    public int compareTo(@SuppressWarnings("NullableProblems") Stage other) {
        return Integer.compare(stage(), other.stage());
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Stage build();

        public abstract Builder stage(int stageNumber);

        public abstract Builder matchAll(boolean mustMatchAll);

        public abstract Builder ruleReferences(List<String> ruleRefs);
    }
}
