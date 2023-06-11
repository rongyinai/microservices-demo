package hipstershop;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.services.*;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsExporter;
import io.opencensus.exporter.stats.stackdriver.StackdriverStatsConfiguration;
import io.opencensus.common.Duration;
import io.opencensus.exporter.trace.jaeger.JaegerExporterConfiguration;
import io.opencensus.exporter.trace.jaeger.JaegerTraceExporter;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceConfiguration;
import io.opencensus.exporter.trace.stackdriver.StackdriverTraceExporter;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import java.io.IOException;
import java.util.concurrent.TimeUnit;


public class EmailService {
    public static final Logger logger= LogManager.getLogger(EmailService.class);

    private Server server;
    private HealthStatusManager healthStatusManager;

    public static final EmailService service = new EmailService();

    public void start() throws Exception{
        int port =Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        healthStatusManager = new HealthStatusManager();

        server = ServerBuilder.forPort(port).addService(new EmailServiceImpl()).addService(healthStatusManager.getHealthService()).build().start();
        logger.info("Em Service started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(
                new Thread(
                        ()->{
                            System.err.println(
                                    "*** shutting down gRPC ads server since JVM is shutting down");
                            EmailService.this.stop();
                            System.err.println("*** server shut down");
                        }
                )
        );
        healthStatusManager.setStatus("", HealthCheckResponse.ServingStatus.SERVING);
    }
    private void stop(){
        if(server!=null){
            healthStatusManager.clearStatus("");
            server.shutdown();
        }
    }

    private static class EmailServiceImpl extends hipstershop.EmailServiceGrpc.EmailServiceImplBase{

        public void sendOrderConfiguration(hipstershop.Demo.SendOrderConfirmationRequest request, StreamObserver<hipstershop.Demo.Empty> responseObserver) {
            logger.info(String.format("A request to send order confirmation email to {} has been received.", request.getEmail()));
            System.out.println(String.format("A request to send order confirmation email to {} has been received.", request.getEmail()));
        }
    }
    public static EmailService getInstance(){
        return service;
    }

    public static void initStats(){
        if(System.getenv("DISABLE_STATS") != null) {
            logger.info("Stats disabled");
            return;
        }
        logger.info("Stats enabled");

        long sleepTime = 10; /* seconds */
        boolean statsExporterRegistered = false;
        for (int i = 0; i < 4; i++) {
            try {
                if (!statsExporterRegistered) {
                    StackdriverStatsExporter.createAndRegister(
                            StackdriverStatsConfiguration.builder()
                                    .setExportInterval(Duration.create(60, 0))
                                    .build());
                    statsExporterRegistered = true;
                }
            } catch (StatusRuntimeException e) {
                if (i == 3) {
                    String s="Failed to register Stackdriver Exporter."
                            + " Stats data will not reported to Stackdriver. Error message: "
                            + e.toString();
                    logger.log(Level.WARN,s,e.getStatus());
                } else {
                    logger.info("Attempt to register Stackdriver Exporter in " + sleepTime + " seconds ");
                    try {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(sleepTime));
                    } catch (Exception se) {
                        logger.log(Level.WARN, "Exception while sleeping" + se.toString(),e.getStatus());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void initTracing(){
        if (System.getenv("DISABLE_TRACING") != null) {
            logger.info("Tracing disabled.");
            return;
        }
        logger.info("Tracing enabled");
        long sleepTime = 10; /* seconds */
        int maxAttempts = 5;
        boolean traceExporterRegistered = false;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                if (!traceExporterRegistered) {
                    StackdriverTraceExporter.createAndRegister(
                            StackdriverTraceConfiguration.builder().build());
                    traceExporterRegistered = true;
                }
            } catch (Exception e) {
                if (i == (maxAttempts - 1)) {
                    logger.log(
                            Level.WARN,
                            "Failed to register Stackdriver Exporter."
                                    + " Tracing data will not reported to Stackdriver. Error message: "
                                    + e.toString());
                } else {
                    logger.info("Attempt to register Stackdriver Exporter in " + sleepTime + " seconds ");
                    try {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(sleepTime));
                    } catch (Exception se) {
                        logger.log(Level.WARN, "Exception while sleeping" + se.toString());
                    }
                }
            }
        }
        logger.info("Tracing enabled - Stackdriver exporter initialized.");
    }
    public static void initJaeger() {
        String jaegerAddr = System.getenv("JAEGER_SERVICE_ADDR");
        if (jaegerAddr != null && !jaegerAddr.isEmpty()) {
            String jaegerUrl = String.format("http://%s/api/traces", jaegerAddr);
            // Register Jaeger Tracing.
            JaegerTraceExporter.createAndRegister(
                    JaegerExporterConfiguration.builder()
                            .setThriftEndpoint(jaegerUrl)
                            .setServiceName("emservice")
                            .build());
            logger.info("Jaeger initialization complete.");
        } else {
            logger.info("Jaeger initialization disabled.");
        }
    }
    public void blockUntilShutdown() throws InterruptedException {
        if(server != null){
            server.awaitTermination();;
        }
    }

    public static void main(String[] args) throws Exception{
        new Thread(
                () -> {
                    initStats();
                    initTracing();
                    initJaeger();
                })
                .start();
        logger.info("AdService starting.");
        final EmailService service = EmailService.getInstance();
        service.start();
        service.blockUntilShutdown();
    }

}
