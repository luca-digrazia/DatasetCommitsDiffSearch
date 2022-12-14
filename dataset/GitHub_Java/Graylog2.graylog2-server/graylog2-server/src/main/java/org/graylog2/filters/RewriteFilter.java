/*
 * Copyright 2012-2014 TORCH GmbH
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
 */

package org.graylog2.filters;

import com.google.inject.internal.util.$Nullable;
import org.graylog2.plugin.GraylogServer;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.RulesEngine;
import org.graylog2.plugin.filters.MessageFilter;

import javax.inject.Inject;

/**
 * @author Lennart Koopmann <lennart@socketfeed.com>
 */
public class RewriteFilter implements MessageFilter {

    private final RulesEngine rulesEngine;

    @Inject
    public RewriteFilter(@$Nullable RulesEngine rulesEngine) {
        this.rulesEngine = rulesEngine;
    }

    @Override
    public boolean filter(Message msg, GraylogServer server) {
        if (rulesEngine != null) {
            rulesEngine.evaluate(msg);
        }

        // false if not expecitly set to true in the rules.
        return msg.getFilterOut();
    }
    
    @Override
    public String getName() {
        return "Rewriter";
    }

}
