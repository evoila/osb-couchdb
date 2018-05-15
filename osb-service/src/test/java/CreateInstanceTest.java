import com.google.gson.JsonObject;
import de.evoila.Application;
import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.model.*;
import de.evoila.cf.broker.repository.ServiceInstanceRepository;
import de.evoila.cf.broker.service.DeploymentServiceImpl;
import de.evoila.cf.cpi.existing.CouchDbExistingServiceFactory;
import de.evoila.cf.broker.service.impl.BindingServiceImpl;
import de.evoila.cf.broker.custom.couchdb.CouchDbCustomImplementation;
import de.evoila.cf.cpi.existing.ExistingServiceFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.lightcouch.CouchDbClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static junit.framework.TestCase.*;

/**
 * @author Marco Di Martino
 */

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = Application.class)
@ContextConfiguration(classes = Application.class, loader = AnnotationConfigContextLoader.class, initializers = ConfigFileApplicationContextInitializer.class)
//@ActiveProfiles(profiles={"default", "singlenode"})
@ActiveProfiles("default")
public class CreateInstanceTest {

    @Autowired
    private CouchDbCustomImplementation conn;

    @Autowired
    private CouchDbExistingServiceFactory couchService;

    @Autowired
    private ExistingServiceFactory service;

    @Autowired
    private DeploymentServiceImpl deploymentService;

    @Autowired
    private BindingServiceImpl bindingService;

    @Autowired
    private ServiceInstanceRepository repository;

    @Autowired
    private ExistingEndpointBean bean;

    /* createInstance:
           call to provisionServiceInstance() Method
           creation of the database, creation of a user and first bind with bindRoleToDatabaseWithPassword
       bindingInstance:
            call to bindService() Method
            create credentials for user
            add user to database
            bindRole to the database
     */


    private ServiceInstance serviceInstance = new ServiceInstance("instance_binding", "service_def", "s", "d", "d", new HashMap<>(), "d");

    private Plan plan;


    @Test
    public void testCreateInstanceFromServiceInstance () throws Exception {

        plan = new Plan();
        plan.setId("1234-5678");
        plan.setPlatform(Platform.EXISTING_SERVICE);
        service.createInstance(serviceInstance, plan, new HashMap<String, Object>());

        CouchDbClient cl = conn.connection(bean.getUsername(), bean.getPassword(), bean.getDatabase(), bean.getHosts()).getCouchDbClient();
        String id = serviceInstance.getUsername();

        assertNotNull(cl.find(JsonObject.class, "org.couchdb.user:"+id));

        String uri = "http://"+bean.getUsername()+":"+bean.getPassword()+"@"+bean.getHosts().get(0).getIp()+":"+bean.getPort()+"/"+"db-"+serviceInstance.getId();

        HttpResponse response = performGet(uri);

        assertEquals(200, response.getStatusLine().getStatusCode());

        String uri_sec = uri+"/_security";
        HttpResponse resp = performGet(uri_sec);
        HttpEntity e = resp.getEntity();
        String ent = EntityUtils.toString(e);

        assertTrue(ent.contains("db-instance_binding"));

        /* binding instance to database */

        List<ServerAddress> list = new ArrayList<>();

        ServerAddress sa = new ServerAddress();
        sa.setIp("127.0.0.1");
        sa.setPort(5984);
        sa.setName("127.0.0.1");

        list.add(sa);

        serviceInstance.setHosts(list);
        repository.addServiceInstance("instance_binding", serviceInstance);

        assertNotNull(repository.getServiceInstance(serviceInstance.getId()));


        ServiceInstanceBindingRequest request = new ServiceInstanceBindingRequest("sample-local", plan.getId());
        ServiceInstanceBindingResponse serviceInstanceBinding = bindingService.createServiceInstanceBinding("binding_id", serviceInstance.getId(),
                request,null);
        assertNotNull(cl.find(JsonObject.class, "org.couchdb.user:"+serviceInstanceBinding.getCredentials().get("username")));


        HttpResponse r = performGet(uri_sec);
        HttpEntity ee = r.getEntity();
        String entity = EntityUtils.toString(ee);

        assertTrue(entity.contains((String)serviceInstanceBinding.getCredentials().get("username")));


    }

    public HttpResponse performGet (String uri) throws Exception {

        HttpHost targetHost = new HttpHost(conn.getService().getConfig().getHost(), conn.getService().getConfig().getPort(), "http");
        AuthCache authCache = new BasicAuthCache();
        authCache.put(targetHost, new BasicScheme());

        CredentialsProvider provider = new BasicCredentialsProvider();
        UsernamePasswordCredentials uspwd = new UsernamePasswordCredentials(
                                            conn.getService().getConfig().getUsername(),
                                            conn.getService().getConfig().getPassword()
                                            );
        provider.setCredentials(AuthScope.ANY, uspwd);

        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(provider);
        context.setAuthCache(authCache);

        HttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
        HttpGet get = new HttpGet(uri);
        return client.execute(get, context);

    }

    @After
    public void delete () throws Exception {
        bindingService.deleteServiceInstanceBinding("binding_id", "1234-5678"); // it's the username
        deploymentService.syncDeleteInstance(getServiceInstance(), plan, service);
    }


    public ServiceInstance getServiceInstance() {
        return serviceInstance;
    }
}
