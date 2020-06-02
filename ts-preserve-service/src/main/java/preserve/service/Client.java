package preserve.service;

import edu.fudan.common.util.JsonUtils;
import edu.fudan.common.util.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import preserve.entity.Contacts;
import preserve.entity.User;

@Service
public class Client {
    @Autowired
    private RestTemplate restTemplate;

    @Cacheable(cacheNames = "contacts",key = "#contactsId")
    public String getContactsById(String contactsId, HttpHeaders httpHeaders) {
        HttpEntity requestGetContactsResult = new HttpEntity(httpHeaders);
        ResponseEntity<Response<Contacts>> reGetContactsResult = restTemplate.exchange(
//                "http://ts-contacts-service:12347/api/v1/contactservice/contacts/" + contactsId,
                "http://ts-contacts-service/api/v1/contactservice/contacts/" + contactsId,
//                "http://localhost:12347/api/v1/contactservice/contacts/" + contactsId,
                HttpMethod.GET,
                requestGetContactsResult,
                new ParameterizedTypeReference<Response<Contacts>>() {
                });
       return JsonUtils.object2Json(reGetContactsResult.getBody());
    }

    @Cacheable(cacheNames = "user",key = "#accountId")
    public User getAccount(String accountId, HttpHeaders httpHeaders) {

        HttpEntity requestEntitySendEmail = new HttpEntity(httpHeaders);
        ResponseEntity<Response<User>> getAccount = restTemplate.exchange(
//                "http://localhost:12342/api/v1/userservice/users/id/" + accountId,
                "http://ts-user-service/api/v1/userservice/users/id/" + accountId,
//                "http://ts-user-service:12342/api/v1/userservice/users/id/" + accountId,
                HttpMethod.GET,
                requestEntitySendEmail,
                new ParameterizedTypeReference<Response<User>>() {
                });
        Response<User> result = getAccount.getBody();
        return result.getData();
    }

    @Cacheable(cacheNames = "station",key = "#stationName")
    public String queryForStationId(String stationName, HttpHeaders httpHeaders) {
        HttpEntity requestQueryForStationId = new HttpEntity(httpHeaders);
        ResponseEntity<Response<String>> reQueryForStationId = restTemplate.exchange(
//                "http://localhost:12345/api/v1/stationservice/stations/id/" + stationName,
                "http://ts-station-service/api/v1/stationservice/stations/id/" + stationName,
//                "http://ts-station-service:12345/api/v1/stationservice/stations/id/" + stationName,
                HttpMethod.GET,
                requestQueryForStationId,
                new ParameterizedTypeReference<Response<String>>() {
                });

        return reQueryForStationId.getBody().getData();
    }
}
