package notification.message;

import edu.fudan.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import notification.entity.NotifyInfo;
import notification.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EmailInfoReceiver {
    @Autowired
    NotificationService service;

    @RabbitListener(bindings = @QueueBinding(
            exchange =@Exchange("email"),
            key = "preserveSuccess",
            value = @Queue("preserveSuccess")
    ))
    public void processPreserve(String msg){
        NotifyInfo notifyInfo= JsonUtils.json2Object(msg,NotifyInfo.class);
        log.info("从队列【preserveEmail】接收到消息"+msg);
        service.preserveSuccess(notifyInfo,null);
    }
}
