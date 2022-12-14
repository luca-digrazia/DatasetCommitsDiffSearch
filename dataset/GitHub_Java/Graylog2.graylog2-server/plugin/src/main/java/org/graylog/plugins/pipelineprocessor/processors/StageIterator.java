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
package org.graylog.plugins.pipelineprocessor.processors;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ArrayListMultimap;
import org.graylog.plugins.pipelineprocessor.ast.Pipeline;
import org.graylog.plugins.pipelineprocessor.ast.Stage;

import java.util.List;
import java.util.Set;

public class StageIterator extends AbstractIterator<List<Stage>> {

    // first and last stage for the given pipelines
    private final int[] extent = new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE};

    // the currentStage is always one before the next one to be returned
    private int currentStage;

    private final ArrayListMultimap<Integer, Stage> stageMultimap = ArrayListMultimap.create();


    public StageIterator(Set<Pipeline> pipelines) {
        if (pipelines.isEmpty()) {
            currentStage = extent[0] = extent[1] = 0;
            return;
        }
        pipelines.forEach(pipeline -> {
            // skip pipelines without any stages, they don't contribute any rules to run
            if (pipeline.stages().isEmpty()) {
                return;
            }
            extent[0] = Math.min(extent[0], pipeline.stages().first().stage());
            extent[1] = Math.max(extent[1], pipeline.stages().last().stage());
        });

        // map each stage number to the corresponding stages
        pipelines.stream()
                .flatMap(pipeline -> pipeline.stages().stream())
                .forEach(stage -> stageMultimap.put(stage.stage(), stage));

        if (extent[0] == Integer.MIN_VALUE) {
            throw new IllegalArgumentException("First stage cannot be at " + Integer.MIN_VALUE);
        }
        // the stage before the first stage.
        currentStage = extent[0] - 1;
    }

    @Override
    protected List<Stage> computeNext() {
        if (currentStage == extent[1]) {
            return endOfData();
        }
        do {
            currentStage++;
            if (currentStage > extent[1]) {
                return endOfData();
            }
        } while (!stageMultimap.containsKey(currentStage));
        return stageMultimap.get(currentStage);
    }
}
