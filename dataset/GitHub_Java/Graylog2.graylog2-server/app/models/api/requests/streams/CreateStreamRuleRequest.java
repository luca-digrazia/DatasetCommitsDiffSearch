package models.api.requests.streams;

import models.api.requests.ApiRequest;

import javax.validation.Valid;

/**
 * @author Dennis Oelkers <dennis@torch.sh>
 */
public class CreateStreamRuleRequest extends ApiRequest {
    public String field;
    public Integer type;
    public String value;
    public Boolean inverted;
}
