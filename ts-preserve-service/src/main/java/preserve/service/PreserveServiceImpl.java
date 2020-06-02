package preserve.service;

import com.netflix.discovery.converters.Auto;
import edu.fudan.common.util.JsonUtils;
import edu.fudan.common.util.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import preserve.entity.*;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

/**
 * @author fdse
 */
@Service
@Slf4j
public class PreserveServiceImpl implements PreserveService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private Client client;

    private static final Logger LOGGER = LoggerFactory.getLogger(PreserveServiceImpl.class);

    @Override
    public Response preserve(OrderTicketsInfo oti, HttpHeaders headers) {
        //1.detect ticket scalper
//        PreserveServiceImpl.LOGGER.info("[Preserve Service] [Step 1] Check Security");
//
//        Response result = checkSecurity(oti.getAccountId(), headers);
//        if (result.getStatus() == 0) {
//            return new Response<>(0, result.getMsg(), null);
//        }
//        PreserveServiceImpl.LOGGER.info("[Preserve Service] [Step 1] Check Security Complete");
        //2.Querying contact information -- modification, mediated by the underlying information micro service
        PreserveServiceImpl.LOGGER.info("[Preserve Service] [Step 2] Find contacts");
        PreserveServiceImpl.LOGGER.info("[Preserve Service] [Step 2] Contacts Id: {}", oti.getContactsId());

        Response<Contacts> gcr = getContactsById(oti.getContactsId(), headers);
        if (gcr.getStatus() == 0) {
            PreserveServiceImpl.LOGGER.info("[Preserve Service][Get Contacts] Fail. {}", gcr.getMsg());
            return new Response<>(0, gcr.getMsg(), null);
        }
        PreserveServiceImpl.LOGGER.info("[Preserve Service][Step 2] Complete");
        //3.Check the info of train and the number of remaining tickets
        PreserveServiceImpl.LOGGER.info("[Preserve Service] [Step 3] Check tickets num");
        TripAllDetailInfo gtdi = new TripAllDetailInfo();

        gtdi.setFrom(oti.getFrom());
        gtdi.setTo(oti.getTo());

        gtdi.setTravelDate(oti.getDate());
        gtdi.setTripId(oti.getTripId());
        PreserveServiceImpl.LOGGER.info("[Preserve Service] [Step 3] TripId: {}", oti.getTripId());
        Response<TripAllDetail> response = getTripAllDetailInformation(gtdi, headers);
        TripAllDetail gtdr = response.getData();
        log.info("TripAllDetail:" + gtdr.toString());
        if (response.getStatus() == 0) {
            PreserveServiceImpl.LOGGER.info("[Preserve Service][Search For Trip Detail Information] {}", response.getMsg());
            return new Response<>(0, response.getMsg(), null);
        } else {
            TripResponse tripResponse = gtdr.getTripResponse();
            log.info("TripResponse:" + tripResponse.toString());
            if (oti.getSeatType() == SeatClass.FIRSTCLASS.getCode()) {
                if (tripResponse.getConfortClass() == 0) {
                    PreserveServiceImpl.LOGGER.info("[Preserve Service][Check seat is enough] ");
                    return new Response<>(0, "Seat Not Enough", null);
                }
            } else {
                if (tripResponse.getEconomyClass() == SeatClass.SECONDCLASS.getCode() && tripResponse.getConfortClass() == 0) {
                    PreserveServiceImpl.LOGGER.info("[Preserve Service][Check seat is enough] ");
                    return new Response<>(0, "Seat Not Enough", null);
                }
            }
        }
        Trip trip = gtdr.getTrip();
        PreserveServiceImpl.LOGGER.info("[Preserve Service] [Step 3] Tickets Enough");
        //4.send the order request and set the order information
        PreserveServiceImpl.LOGGER.info("[Preserve Service] [Step 4] Do Order");
        Contacts contacts = gcr.getData();
        Order order = new Order();
        UUID orderId = UUID.randomUUID();
        order.setId(orderId);
        order.setTrainNumber(oti.getTripId());
        order.setAccountId(UUID.fromString(oti.getAccountId()));

        String fromStationId = queryForStationId(oti.getFrom(), headers);
        String toStationId = queryForStationId(oti.getTo(), headers);

        order.setFrom(fromStationId);
        order.setTo(toStationId);
        order.setBoughtDate(new Date());
        order.setStatus(OrderStatus.NOTPAID.getCode());
        order.setContactsDocumentNumber(contacts.getDocumentNumber());
        order.setContactsName(contacts.getName());
        order.setDocumentType(contacts.getDocumentType());

        Travel query = new Travel();
        query.setTrip(trip);
        query.setStartingPlace(oti.getFrom());
        query.setEndPlace(oti.getTo());
        query.setDepartureTime(new Date());

        HttpEntity requestEntity = new HttpEntity(query, headers);
        ResponseEntity<Response<TravelResult>> re = restTemplate.exchange(
//                "http://ts-ticketinfo-service:15681/api/v1/ticketinfoservice/ticketinfo",
                "http://ts-ticketinfo-service/api/v1/ticketinfoservice/ticketinfo",
//                "http://localhost:15681/api/v1/ticketinfoservice/ticketinfo",
                HttpMethod.POST,
                requestEntity,
                new ParameterizedTypeReference<Response<TravelResult>>() {
                });
        TravelResult resultForTravel = re.getBody().getData();

        order.setSeatClass(oti.getSeatType());
        PreserveServiceImpl.LOGGER.info("[Preserve Service][Order] Order Travel Date: {}", oti.getDate().toString());
        order.setTravelDate(oti.getDate());
        order.setTravelTime(gtdr.getTripResponse().getStartingTime());

        //Dispatch the seat
        if (oti.getSeatType() == SeatClass.FIRSTCLASS.getCode()) {
            Ticket ticket =
                    dipatchSeat(oti.getDate(),
                            order.getTrainNumber(), fromStationId, toStationId,
                            SeatClass.FIRSTCLASS.getCode(), headers);
            order.setSeatNumber("" + ticket.getSeatNo());
            order.setSeatClass(SeatClass.FIRSTCLASS.getCode());
            order.setPrice(resultForTravel.getPrices().get("confortClass"));
        } else {
            Ticket ticket =
                    dipatchSeat(oti.getDate(),
                            order.getTrainNumber(), fromStationId, toStationId,
                            SeatClass.SECONDCLASS.getCode(), headers);
            order.setSeatClass(SeatClass.SECONDCLASS.getCode());
            order.setSeatNumber("" + ticket.getSeatNo());
            order.setPrice(resultForTravel.getPrices().get("economyClass"));
        }

        PreserveServiceImpl.LOGGER.info("[Preserve Service][Order Price] Price is: {}", order.getPrice());

        Response<Order> cor = createOrder(order, headers);
        if (cor.getStatus() == 0) {
            PreserveServiceImpl.LOGGER.info("[Preserve Service][Create Order Fail] Create Order Fail.  Reason: {}", cor.getMsg());
            return new Response<>(0, cor.getMsg(), null);
        }
        PreserveServiceImpl.LOGGER.info("[Preserve Service] [Step 4] Do Order Complete");

        Response returnResponse = new Response<>(1, "Success.", cor.getMsg());
        //5.Check insurance options
        if (oti.getAssurance() == 0) {
            PreserveServiceImpl.LOGGER.info("[Preserve Service][Step 5] Do not need to buy assurance");
        } else {
            Response addAssuranceResult = addAssuranceForOrder(
                    oti.getAssurance(), cor.getData().getId().toString(), headers);
            if (addAssuranceResult.getStatus() == 1) {
                PreserveServiceImpl.LOGGER.info("[Preserve Service][Step 5] Buy Assurance Success");
            } else {
                PreserveServiceImpl.LOGGER.info("[Preserve Service][Step 5] Buy Assurance Fail.");
                returnResponse.setMsg("Success.But Buy Assurance Fail.");
            }
        }

        //6.Increase the food order
        if (oti.getFoodType() != 0) {

            FoodOrder foodOrder = new FoodOrder();
            foodOrder.setOrderId(cor.getData().getId());
            foodOrder.setFoodType(oti.getFoodType());
            foodOrder.setFoodName(oti.getFoodName());
            foodOrder.setPrice(oti.getFoodPrice());

            if (oti.getFoodType() == 2) {
                foodOrder.setStationName(oti.getStationName());
                foodOrder.setStoreName(oti.getStoreName());
                PreserveServiceImpl.LOGGER.info("[Food Service]!!!!!!!!!!!!!!!foodstore= {}   {}   {}", foodOrder.getFoodType(), foodOrder.getStationName(), foodOrder.getStoreName());
            }
            Response afor = createFoodOrder(foodOrder, headers);
            if (afor.getStatus() == 1) {
                PreserveServiceImpl.LOGGER.info("[Preserve Service][Step 6] Buy Food Success");
            } else {
                PreserveServiceImpl.LOGGER.info("[Preserve Service][Step 6] Buy Food Fail.");
                returnResponse.setMsg("Success.But Buy Food Fail.");
            }
        } else {
            PreserveServiceImpl.LOGGER.info("[Preserve Service][Step 6] Do not need to buy food");
        }

        //7.add consign
        if (null != oti.getConsigneeName() && !"".equals(oti.getConsigneeName())) {

            Consign consignRequest = new Consign();
            consignRequest.setOrderId(cor.getData().getId());
            consignRequest.setAccountId(cor.getData().getAccountId());
            consignRequest.setHandleDate(oti.getHandleDate());
            consignRequest.setTargetDate(cor.getData().getTravelDate().toString());
            consignRequest.setFrom(cor.getData().getFrom());
            consignRequest.setTo(cor.getData().getTo());
            consignRequest.setConsignee(oti.getConsigneeName());
            consignRequest.setPhone(oti.getConsigneePhone());
            consignRequest.setWeight(oti.getConsigneeWeight());
            consignRequest.setWithin(oti.isWithin());
            log.info("CONSIGN INFO : " + consignRequest.toString());
            Response icresult = createConsign(consignRequest, headers);

            if (icresult.getStatus() == 1) {
                PreserveServiceImpl.LOGGER.info("[Preserve Service][Step 7] Consign Success");
            } else {
                PreserveServiceImpl.LOGGER.info("[Preserve Service][Step 7] Consign Fail.");
                returnResponse.setMsg("Consign Fail.");
            }
        } else {
            PreserveServiceImpl.LOGGER.info("[Preserve Service][Step 7] Do not need to consign");
        }

        //8.send notification
        PreserveServiceImpl.LOGGER.info("[Preserve Service]");

        User getUser = getAccount(order.getAccountId().toString(), headers);

        NotifyInfo notifyInfo = new NotifyInfo();
        notifyInfo.setDate(new Date().toString());

        notifyInfo.setEmail(getUser.getEmail());
        notifyInfo.setStartingPlace(order.getFrom());
        notifyInfo.setEndPlace(order.getTo());
        notifyInfo.setUsername(getUser.getUserName());
        notifyInfo.setSeatNumber(order.getSeatNumber());
        notifyInfo.setOrderNumber(order.getId().toString());
        notifyInfo.setPrice(order.getPrice());
        notifyInfo.setSeatClass(SeatClass.getNameByCode(order.getSeatClass()));
        notifyInfo.setStartingTime(order.getTravelTime().toString());

        //发送mq消息
        PreserveServiceImpl.LOGGER.info("[Preserve Service][Send Email]");
        amqpTemplate.convertAndSend("email", "preserveSuccess", JsonUtils.object2Json(notifyInfo));
//        sendEmail(notifyInfo,headers);
        return returnResponse;
    }

    public Ticket dipatchSeat(Date date, String tripId, String startStationId, String endStataionId, int seatType, HttpHeaders httpHeaders) {
        Seat seatRequest = new Seat();
        seatRequest.setTravelDate(date);
        seatRequest.setTrainNumber(tripId);
        seatRequest.setStartStation(startStationId);
        seatRequest.setDestStation(endStataionId);
        seatRequest.setSeatType(seatType);

        HttpEntity requestEntityTicket = new HttpEntity(seatRequest, httpHeaders);
        ResponseEntity<Response<Ticket>> reTicket = restTemplate.exchange(
//                "http://ts-seat-service:18898/api/v1/seatservice/seats",
                "http://ts-seat-service/api/v1/seatservice/seats",
//                "http://localhost:18898/api/v1/seatservice/seats",
                HttpMethod.POST,
                requestEntityTicket,
                new ParameterizedTypeReference<Response<Ticket>>() {
                });

        return reTicket.getBody().getData();
    }

    public boolean sendEmail(NotifyInfo notifyInfo, HttpHeaders httpHeaders) {
        PreserveServiceImpl.LOGGER.info("[Preserve Service][Send Email]");
        HttpEntity requestEntitySendEmail = new HttpEntity(notifyInfo, httpHeaders);
        ResponseEntity<Boolean> reSendEmail = restTemplate.exchange(
//                "http://localhost:17853/api/v1/notifyservice/notification/order_cancel_success",
//                "http://ts-notification-service:17853/api/v1/notifyservice/notification/order_cancel_success",
                "http://ts-notification-service/api/v1/notifyservice/notification/order_cancel_success",
                HttpMethod.POST,
                requestEntitySendEmail,
                Boolean.class);

        return reSendEmail.getBody();
    }

    public User getAccount(String accountId, HttpHeaders httpHeaders) {
        PreserveServiceImpl.LOGGER.info("[Cancel Order Service][Get Order By Id]");
        return client.getAccount(accountId,httpHeaders);
    }

    private Response addAssuranceForOrder(int assuranceType, String orderId, HttpHeaders httpHeaders) {
        PreserveServiceImpl.LOGGER.info("[Preserve Service][Add Assurance For Order]");
        HttpEntity requestAddAssuranceResult = new HttpEntity(httpHeaders);
        ResponseEntity<Response> reAddAssuranceResult = restTemplate.exchange(
                "http://ts-assurance-service:18888/api/v1/assuranceservice/assurances/" + assuranceType + "/" + orderId,
                HttpMethod.GET,
                requestAddAssuranceResult,
                Response.class);

        return reAddAssuranceResult.getBody();
    }

    public String queryForStationId(String stationName, HttpHeaders httpHeaders) {
        PreserveServiceImpl.LOGGER.info("[Preserve Other Service][Get Station Name]");
        return client.queryForStationId(stationName, httpHeaders);
    }

    private Response<TripAllDetail> getTripAllDetailInformation(TripAllDetailInfo gtdi, HttpHeaders httpHeaders) {
        PreserveServiceImpl.LOGGER.info("[Preserve Other Service][Get Trip All Detail Information] Getting....");

        HttpEntity requestGetTripAllDetailResult = new HttpEntity(gtdi, httpHeaders);
        ResponseEntity<Response<TripAllDetail>> reGetTripAllDetailResult = restTemplate.exchange(
                "http://ts-travel-service/api/v1/travelservice/trip_detail",
//                "http://ts-travel-service:12346/api/v1/travelservice/trip_detail",
//                "http://localhost:12346/api/v1/travelservice/trip_detail",
                HttpMethod.POST,
                requestGetTripAllDetailResult,
                new ParameterizedTypeReference<Response<TripAllDetail>>() {
                });

        return reGetTripAllDetailResult.getBody();
    }

    private Response<Contacts> getContactsById(String contactsId, HttpHeaders httpHeaders) {
        PreserveServiceImpl.LOGGER.info("[Preserve Other Service][Get Contacts By Id] Getting....");
        Response reponse = JsonUtils.json2Object(client.getContactsById(contactsId, httpHeaders), Response.class);
        Response<Contacts> contactsResponse = new Response<>();
        contactsResponse.setStatus(reponse.getStatus());
        contactsResponse.setData(JsonUtils.json2Object(JsonUtils.object2Json(reponse.getData()), Contacts.class));

        contactsResponse.setMsg(reponse.getMsg());
        return contactsResponse;
    }

    private Response createOrder(Order coi, HttpHeaders httpHeaders) {
        PreserveServiceImpl.LOGGER.info("[Preserve Other Service][Get Contacts By Id] Creating....");

        HttpEntity requestEntityCreateOrderResult = new HttpEntity(coi, httpHeaders);
        ResponseEntity<Response<Order>> reCreateOrderResult = restTemplate.exchange(
//                "http://localhost:12031/api/v1/orderservice/order",
                "http://ts-order-service/api/v1/orderservice/order",
//                "http://ts-order-service:12031/api/v1/orderservice/order",
                HttpMethod.POST,
                requestEntityCreateOrderResult,
                new ParameterizedTypeReference<Response<Order>>() {
                });

        return reCreateOrderResult.getBody();
    }

    private Response createFoodOrder(FoodOrder afi, HttpHeaders httpHeaders) {
        PreserveServiceImpl.LOGGER.info("[Preserve Service][Add food Order] Creating....");

        HttpEntity requestEntityAddFoodOrderResult = new HttpEntity(afi, httpHeaders);
        ResponseEntity<Response> reAddFoodOrderResult = restTemplate.exchange(
                "http://ts-food-service:18856/api/v1/foodservice/orders",
                HttpMethod.POST,
                requestEntityAddFoodOrderResult,
                Response.class);

        return reAddFoodOrderResult.getBody();
    }

    private Response createConsign(Consign cr, HttpHeaders httpHeaders) {
        PreserveServiceImpl.LOGGER.info("[Preserve Service][Add Condign] Creating....");

        HttpEntity requestEntityResultForTravel = new HttpEntity(cr, httpHeaders);
        ResponseEntity<Response> reResultForTravel = restTemplate.exchange(
                "http://ts-consign-service:16111/api/v1/consignservice/consigns",
                HttpMethod.POST,
                requestEntityResultForTravel,
                Response.class);
        return reResultForTravel.getBody();
    }

}
