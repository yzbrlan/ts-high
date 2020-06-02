package fdse.microservice.service;

import edu.fudan.common.util.JsonUtils;
import edu.fudan.common.util.Response;
import fdse.microservice.entity.TrainType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class Client {
    @Autowired
    private RestTemplate restTemplate;

    @Cacheable(cacheNames = "train", key = "#trainTypeId")
    public TrainType queryTrainType(String trainTypeId, HttpHeaders headers) {
        HttpEntity requestEntity = new HttpEntity(headers);
        ResponseEntity<Response> re = restTemplate.exchange(
//                "http://ts-train-service:14567/api/v1/trainservice/trains/" + trainTypeId,
                "http://ts-train-service/api/v1/trainservice/trains/" + trainTypeId,
//                "http://localhost:14567/api/v1/trainservice/trains/" + trainTypeId,
                HttpMethod.GET,
                requestEntity,
                Response.class);
        Response response = re.getBody();

        return JsonUtils.conveterObject(response.getData(), TrainType.class);
    }
}
