package top.xywu.apigateway.exception;

public class RateLimitException extends RuntimeException{
    public RateLimitException(String msg){
        super(msg);
    }
    public RateLimitException(){
        super();
    }
}
