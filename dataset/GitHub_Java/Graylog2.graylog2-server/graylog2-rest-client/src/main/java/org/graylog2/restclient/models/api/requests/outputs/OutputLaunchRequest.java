package org.graylog2.restclient.models.api.requests.outputs;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.graylog2.restclient.models.api.requests.ApiRequest;
import play.data.validation.Constraints;

import java.util.List;
import java.util.Map;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class OutputLaunchRequest extends ApiRequest {
    @Constraints.Required
    public String title;
    @Constraints.Required
    public String type;
    public Map<String, Object> configuration;
    @JsonProperty("creator_user_id")
    public String creatorUserId;
    @JsonProperty("streams")
    public List<String> streams;

    @Override
    public String toString() {
        return "OutputLaunchRequest{" +
                "title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", configuration=" + configuration +
                ", creatorUserId='" + creatorUserId + '\'' +
                ", streams=" + streams +
                '}';
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getConfiguration() {
        return configuration;
    }

    public void setConfiguration(Map<String, Object> configuration) {
        this.configuration = configuration;
    }

    public String getCreatorUserId() {
        return creatorUserId;
    }

    public void setCreatorUserId(String creatorUserId) {
        this.creatorUserId = creatorUserId;
    }

    public List<String> getStreams() {
        return streams;
    }

    public void setStreams(List<String> streams) {
        this.streams = streams;
    }
}
