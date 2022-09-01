package models;

import com.google.common.collect.Lists;
import lib.APIException;
import lib.ApiClient;
import lib.plugin.configuration.RequestedConfigurationField;
import models.api.requests.alarmcallbacks.CreateAlarmCallbackRequest;
import models.api.responses.alarmcallbacks.*;
import play.mvc.Http;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class AlarmCallbackService {
    private final ApiClient apiClient;
    private final AlarmCallback.Factory alarmCallbackFactory;

    @Inject
    public AlarmCallbackService(ApiClient apiClient,
                                AlarmCallback.Factory alarmCallbackFactory) {
        this.apiClient = apiClient;
        this.alarmCallbackFactory = alarmCallbackFactory;
    }

    public List<AlarmCallback> all(String streamId) throws IOException, APIException {
        GetAlarmCallbacksResponse response = apiClient.get(GetAlarmCallbacksResponse.class)
                .path("/streams/"+streamId+"/alarmcallbacks").expect(Http.Status.OK).execute();

        List<AlarmCallback> result = Lists.newArrayList();
        for (AlarmCallbackSummaryResponse callbackResponse : response.alarmcallbacks) {
            result.add(alarmCallbackFactory.fromSummaryResponse(streamId, callbackResponse));
        }

        return result;
    }

    public AlarmCallback get(String streamId, String alarmCallbackId) throws IOException, APIException {
        AlarmCallbackSummaryResponse response = apiClient.get(AlarmCallbackSummaryResponse.class)
                .path("/streams/"+streamId+"/alarmcallbacks/"+alarmCallbackId).expect(Http.Status.OK).execute();

        return alarmCallbackFactory.fromSummaryResponse(streamId, response);
    }

    public CreateAlarmCallbackResponse create(String streamId, CreateAlarmCallbackRequest request) throws IOException, APIException {
        return apiClient.post(CreateAlarmCallbackResponse.class).path("/streams/"+streamId+"/alarmcallbacks").body(request).expect(Http.Status.CREATED).execute();
    }

    public Map<String, GetSingleAvailableAlarmCallbackResponse> available(String streamId) throws IOException, APIException {
        GetAvailableAlarmCallbacksResponse response = apiClient.get(GetAvailableAlarmCallbacksResponse.class)
                .path("/streams/"+streamId+"/alarmcallbacks/available").expect(Http.Status.OK).execute();

        return response.types;
    }

    public void delete(String streamId, String alarmCallbackId) throws IOException, APIException {
        apiClient.delete().path("/streams/"+streamId+"/alarmcallbacks/"+alarmCallbackId).expect(Http.Status.NO_CONTENT).execute();
    }
}
