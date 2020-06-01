package preserve.message;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MqReceiver {
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue("myQueue"),
            exchange =@Exchange("myExchange")
    ))
    public void process(String message){
        log.info(message);
    }
}
