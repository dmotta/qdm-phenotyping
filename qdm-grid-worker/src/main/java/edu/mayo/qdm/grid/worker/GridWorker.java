package edu.mayo.qdm.grid.worker;

import edu.mayo.qdm.executor.Executor;
import edu.mayo.qdm.executor.ExecutorFactory;
import edu.mayo.qdm.executor.Results;
import edu.mayo.qdm.grid.common.WorkerExecutionRequest;
import edu.mayo.qdm.grid.common.WorkerRegistrationRequest;
import org.apache.camel.CamelContext;
import org.apache.camel.Handler;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;

@Component("gridWorker")
public class GridWorker implements InitializingBean {

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private CamelContext camelContext;

    private Executor executor;

    public static void main(String[] args){
        if(args == null || args.length != 4){
            throw new IllegalArgumentException();
        }
        AbstractApplicationContext context = new ClassPathXmlApplicationContext("qdm-grid-worker-context.xml");
        context.registerShutdownHook();

        context.getBean(GridWorker.class).register(args[0], Integer.parseInt(args[1]), args[2], Integer.parseInt(args[3]));
    }

    public void register(String workerHostName, int workerPort, String masterHostName, int masterPort){
        final String workerPortString = Integer.toString(workerPort);
        final String masterPortString = Integer.toString(masterPort);

        try {
            this.camelContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("netty:tcp://localhost:"+workerPortString+"?sync=true").to("bean:gridWorker");
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        String uri = "netty:tcp://"+workerHostName+":"+workerPortString+"?sync=true";

        this.producerTemplate.requestBody("netty:tcp://"+masterHostName+":"+masterPortString+"?sync=true", new WorkerRegistrationRequest(uri));

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.executor = ExecutorFactory.instance().getExecutor();
    }

    @Handler
    public Results process(WorkerExecutionRequest workerExecutionRequest) {
        return this.executor.execute(
                workerExecutionRequest.getPatients(),
                workerExecutionRequest.getQdmXml(),
                workerExecutionRequest.getMeasurementPeriod(),
                workerExecutionRequest.getValueSetDefinitions());
    }

}