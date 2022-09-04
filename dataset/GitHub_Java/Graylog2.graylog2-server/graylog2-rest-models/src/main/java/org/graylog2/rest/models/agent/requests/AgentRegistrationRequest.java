package org.graylog2.rest.models.agent.requests;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import org.graylog2.rest.models.agent.AgentNodeDetails;

@AutoValue
@JsonAutoDetect
public abstract class AgentRegistrationRequest {

    @JsonProperty
    public abstract String id();

    @JsonProperty("node_id")
    public abstract String nodeId();

    @JsonProperty("node_details")
    public abstract AgentNodeDetails nodeDetails();

    @JsonCreator
    public static AgentRegistrationRequest create(@JsonProperty("id") String id,
                                                  @JsonProperty("node_id") String nodeId,
                                                  @JsonProperty("node_details") AgentNodeDetails nodeDetails) {
        return new AutoValue_AgentRegistrationRequest(id, nodeId, nodeDetails);
    }
}
