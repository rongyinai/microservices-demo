package hipstershop;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;
public class EmailServiceClient {
    private final ManagedChannel channel;
    private static final Logger logger = LogManager.getLogger(EmailServiceClient.class);
    private final hipstershop.EmailServiceGrpc.EmailServiceBlockingStub stub;

    private EmailServiceClient(ManagedChannel channel){
        this.channel = channel;
        stub = hipstershop.EmailServiceGrpc.newBlockingStub(channel);
    }
    public EmailServiceClient(String host, int port){
        this(ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build());
    }
    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
    public void operate(){
        hipstershop.Demo.SendOrderConfirmationRequest request = hipstershop.Demo.SendOrderConfirmationRequest.newBuilder().setEmail("Hello RPGC").build();
    }

    public static void main(String[] args) {
        try {
            EmailServiceClient serviceClient = new EmailServiceClient("localhost", 9090);
            serviceClient.operate();
            serviceClient.shutdown();
        }catch (Exception e){
            System.out.println(e);
        }
        logger.info("Exiting AdServiceClient...");
    }
}
