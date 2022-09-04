/**
 * The MIT License
 * Copyright (c) 2012 TORCH GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.graylog2.restclient.lib;

import com.google.common.collect.*;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.graylog2.restclient.models.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class ServerNodes {
    private static final Logger log = LoggerFactory.getLogger(ServerNodes.class);
    private final CopyOnWriteArrayList<Node> serverNodes = Lists.newCopyOnWriteArrayList();
    private final BiMap<Node, Node> configuredNodes = Maps.synchronizedBiMap(HashBiMap.<Node, Node>create());
    private final Random random = new Random();

    @Inject
    private ServerNodes(Node.Factory nodeFactory, @Named("Initial Nodes") URI[] nodeAddresses) {
        for (URI nodeAddress : nodeAddresses) {
            final Node configuredNode = nodeFactory.fromTransportAddress(nodeAddress);
            configuredNodes.put(configuredNode, configuredNode);
        }

        log.debug("Creating ServerNodes with initial nodes {}", configuredNodes.keySet());
        // resolve the configured nodes:
        // we only know a transport address where we can reach them, but we don't know any node ids yet.
        // thus we do not know anything about them, and cannot even match them to node information coming
        // back from /system/cluster -> those all have node ids
        // ServerNodesRefreshService will do this for us, this class only deals with picking nodes from a list,
        // but does not update itself from external sources, this makes testing much easier
    }

    /**
     * Retrieves all currently active nodes.
     *
     * @return list of currently active nodes
     */
    public List<Node> all() {
        return all(false);
    }

    public List<Node> all(boolean allowInactive) {
        final Iterator<Node> nodeIterator;
        if (allowInactive) {
            nodeIterator = serverNodes.iterator();
        }
        else {
            nodeIterator = skipInactive(serverNodes);
        }
        final ImmutableList<Node> nodes = ImmutableList.copyOf(nodeIterator);
        if (!allowInactive && nodes.isEmpty()) {
            throw new Graylog2ServerUnavailableException();
        }
        return nodes;
    }

    public Node master() {
        final List<Node> all = all(false);

        if (all.isEmpty()) {
            throw new Graylog2ServerUnavailableException();
        }

        for (Node node : all) {
            if (node.isMaster()) {
                return node;
            }
        }

        // No active master node was found.
        throw new Graylog2MasterUnavailableException();
    }

    /**
     * Retrieve a random single active node.
     *
     * @return an active node
     */
    public Node any() {
        return any(false);
    }

    public Node any(boolean allowInactive) {
        final List<Node> all = all(allowInactive);
        if (all.isEmpty()) {
            throw new Graylog2ServerUnavailableException();
        }
        final int i = random.nextInt(all.size());
        return all.get(i);
    }

    /**
     * Register nodes in the list of active nodes.
     *
     * The passed nodes are taken to be active, until this process knows it cannot reach them.
     *
     * @param nodes Nodes known to exist in the cluster
     */
    public void put(Collection<Node> nodes) {
        HashSet<Node> existingNodes = Sets.newHashSet(serverNodes);
        for (Node newNode : nodes) {
            for (Node serverNode : existingNodes) {
                log.debug("Checking new node {} against existing node {}", newNode, serverNode);
                if (newNode.equals(serverNode)) {
                    serverNode.merge(newNode);
                    existingNodes.remove(serverNode);
                    break;
                }
            }
        }

        serverNodes.addAllAbsent(nodes);
        logServerNodesState();
    }

    private void logServerNodesState() {
        if (log.isDebugEnabled()) {
            StringBuilder b = new StringBuilder();
            b.append("Node List").append('\n');
            for (Node serverNode : serverNodes) {
                b.append(' ');
                if (serverNode.isMaster()) {
                    b.append("* ");
                } else {
                    b.append("  ");
                }
                b.append(serverNode.getNodeId())
                        .append('\t')
                        .append(serverNode.getTransportAddress())
                        .append('\t')
                        .append(serverNode.isActive() ? "active" : "inactive");
                if (serverNode.getFailureCount() > 0) {
                    b.append('\t').append("failures: ").append(serverNode.getFailureCount());
                }
                final Node linkedNode = configuredNodes.inverse().get(serverNode);
                if (linkedNode != null) {
                    b.append('\t').append("via config node ").append(linkedNode.getTransportAddress());
                }
                b.append('\n');
            }
            log.debug(b.toString());
        }
    }

    public Map<String, Node> asMap() {
        Map<String, Node> map = Maps.newHashMap();
        for (Node serverNode : ImmutableList.copyOf(skipInactive(serverNodes))) {
            map.put(serverNode.getNodeId(), serverNode);
        }

        return map;
    }

    private Iterator<Node> skipInactive(final Iterable<Node> iterable) {
        return new AbstractIterator<Node>() {
            Iterator<Node> in = iterable.iterator();
            @Override
            protected Node computeNext() {
                while (in.hasNext()) {
                    final Node next = in.next();
                    if (next.isActive()) {
                        return next;
                    }
                }
                return endOfData();
            }
        };
    }

    public List<Node> getConfiguredNodes() {
        return ImmutableList.copyOf(configuredNodes.keySet());
    }

    public void linkConfiguredNode(Node configuredNode, Node resolvedNode) {
        configuredNodes.put(configuredNode, resolvedNode);
    }

    public Node getDiscoveredNodeVia(Node configuredNode) {
        final Node node = configuredNodes.get(configuredNode);
        return node;
    }

    public Node getConfigNodeOf(Node serverNode) {
        return configuredNodes.inverse().get(serverNode);
    }

    public int connectedNodesCount() {
        return Iterators.size(skipInactive(serverNodes));
    }

    public int totalNodesCount() {
        return serverNodes.size();
    }

    public boolean isConnected() {
        return skipInactive(serverNodes).hasNext();
    }
}
