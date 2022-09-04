/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.rest.resources.system.agent;

import com.github.joschi.jadconfig.util.Duration;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import org.graylog2.Configuration;
import org.graylog2.agents.Agent;
import org.graylog2.agents.AgentNodeDetails;
import org.graylog2.agents.AgentService;
import org.graylog2.database.NotFoundException;
import org.graylog2.rest.models.agent.responses.AgentList;
import org.graylog2.rest.models.agent.responses.AgentSummary;
import org.graylog2.rest.resources.RestResourceBaseTest;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(value = MockitoJUnitRunner.class)
public class AgentResourceTest extends RestResourceBaseTest {
    private AgentResource resource;
    private List<Agent> agents;

    @Mock
    private AgentService agentService;

    @Before
    public void setUp() throws Exception {
        this.agents = getDummyAgentList();
        final Configuration config = mock(Configuration.class);
        when(config.getAgentInactiveThreshold()).thenReturn(Duration.minutes(1));
        this.resource = new AgentResource(agentService, config);
        when(agentService.all()).thenReturn(agents);
    }

    @Test
    public void testList() throws Exception {
        final AgentList response = this.resource.list();

        assertNotNull(response);
        assertNotNull(response.agents());
        assertEquals("Agent list should be of same size as dummy list", agents.size(), response.agents().size());
    }

    @Test(expected = NotFoundException.class)
    public void testGetNotExisting() throws Exception {
        final AgentSummary response = this.resource.get("Nonexisting");

        assertNull(response);
    }

    @Test
    public void testGet() throws Exception {
        final Agent agent = agents.get(agents.size() - 1);
        when(agentService.findById(agent.getId())).thenReturn(agent);
        final AgentSummary agentSummary = mock(AgentSummary.class);
        when(agent.toSummary(any(Function.class))).thenReturn(agentSummary);

        final AgentSummary response = this.resource.get(agent.getId());

        assertNotNull(response);
        assertEquals(agentSummary, response);
    }

    private Agent getDummyAgent(String id, String nodeId, DateTime lastSeen, String operatingSystem) {
        final AgentNodeDetails agentNodeDetails = mock(AgentNodeDetails.class);
        when(agentNodeDetails.operatingSystem()).thenReturn(operatingSystem);

        final Agent agent = mock(Agent.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getNodeId()).thenReturn(nodeId);
        when(agent.getLastSeen()).thenReturn(lastSeen);
        when(agent.getNodeDetails()).thenReturn(agentNodeDetails);
        when(agent.getOperatingSystem()).thenReturn(operatingSystem);

        return agent;
    }
    private List<Agent> getDummyAgentList() {
        final Agent agent1 = getDummyAgent("agent1id", "agent1nodeid", DateTime.now(), "DummyOS 1.0");
        final Agent agent2 = getDummyAgent("agent2id", "agent2nodeid", DateTime.now(), "DummyOS 1.0");
        final Agent agent3 = getDummyAgent("agent3id", "agent3nodeid", DateTime.now(), "DummyOS 1.0");

        return Lists.newArrayList(agent1, agent2, agent3);
    }
}