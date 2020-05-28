package seat.controller;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import seat.entity.Seat;
import seat.service.SeatService;

import javax.xml.ws.Response;

import static org.springframework.http.ResponseEntity.ok;

/**
 * @author fdse
 */
@RestController
@RequestMapping("/api/v1/seatservice")
public class SeatController {

    @Autowired
    private SeatService seatService;

    @GetMapping(path = "/welcome")
    public String home() {
        return "Welcome to [ Seat Service ] !";
    }

    /**
     * Assign seats by seat request
     *
     * @param seatRequest seat request
     * @param headers headers
     * @return HttpEntity
     */
    @CrossOrigin(origins = "*")
    @PostMapping(value = "/seats")
    @HystrixCommand(fallbackMethod = "createFallback",
            commandProperties = {
                    @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "5000")
            })
    public HttpEntity create(@RequestBody Seat seatRequest, @RequestHeader HttpHeaders headers) {
        return ok(seatService.distributeSeat(seatRequest, headers));
    }
    private HttpEntity createFallback(@RequestBody Seat seatRequest, @RequestHeader HttpHeaders headers){
        return ok(new edu.fudan.common.util.Response<>(0, "create seat fail", null));
    }

    /**
     * get left ticket of interval
     * query specific interval residual
     *
     * @param seatRequest seat request
     * @param headers headers
     * @return HttpEntity
     */
    @CrossOrigin(origins = "*")
    @PostMapping(value = "/seats/left_tickets")
    @HystrixCommand(fallbackMethod = "getFallback")
    public HttpEntity getLeftTicketOfInterval(@RequestBody Seat seatRequest, @RequestHeader HttpHeaders headers) {
        // int
        return ok(seatService.getLeftTicketOfInterval(seatRequest, headers));
    }

    private HttpEntity getFallback(@RequestBody Seat seatRequest, @RequestHeader HttpHeaders headers){
        return ok(new edu.fudan.common.util.Response<>(0, "get left ticket of interval fail", null));
    }
}
