import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.evoila.Application;
import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.model.*;
import de.evoila.cf.broker.persistence.repository.BindingRepositoryImpl;
import de.evoila.cf.broker.repository.ServiceInstanceRepository;
import de.evoila.cf.broker.service.impl.DeploymentServiceImpl;
import de.evoila.cf.cpi.existing.CouchDbExistingServiceFactory;
import de.evoila.cf.broker.custom.couchdb.UserDocument;
import de.evoila.cf.broker.service.impl.BindingServiceImpl;
import de.evoila.cf.broker.custom.couchdb.CouchDbCustomImplementation;
import de.evoila.cf.broker.custom.couchdb.CouchDbService;
import de.evoila.cf.cpi.existing.ExistingServiceFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.lightcouch.CouchDbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertEquals;

/**
* @author Marco Di Martino
*/

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = Application.class)
@ContextConfiguration(classes = Application.class, loader = AnnotationConfigContextLoader.class, initializers = ConfigFileApplicationContextInitializer.class)
//@ActiveProfiles(profiles={"local", "test"})
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClusterTest {

    @Autowired
    private CouchDbCustomImplementation conn;

    @Autowired
    private CouchDbService couchServ;
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
    private BindingRepositoryImpl bindingRepository;

    @Autowired
    private ExistingEndpointBean bean;

    private ServiceInstance serviceInstance = new ServiceInstance("instance_binding", "service_def", "s", "d", "d", new HashMap<>(), "d");

    private Plan plan = new Plan();

    private Logger log = LoggerFactory.getLogger(getClass());

    private static final String DB="db-";


    /*
    * db name: db-instance_binding
    * replication is enabled:
    * test if db is created on every vm
    *
    * */

    @Test
    public void testA_dbCreation() throws Exception {
        CouchDbService service1 = conn.connection(serviceInstance, plan, true, null);

        String database = serviceInstance.getId();
        plan.setPlatform(Platform.EXISTING_SERVICE);
        plan.setId("1234-5678");

        couchService.createInstance(serviceInstance, plan, new HashMap<String, Object>());

        assertTrue(service1.getCouchDbClient().context().getAllDbs().contains(DB+database)); // db is on first node

        List<ServerAddress> ip_node = new ArrayList<>();
        ip_node.add(bean.getHosts().get(1));

        couchServ.createConnection(bean.getUsername(),bean.getPassword(), bean.getDatabase(), bean.getHosts());
        CouchDbClient service2 = couchServ.getCouchDbClient();
        //CouchDbClient service2 = conn.connection(bean.getUsername(), bean.getPassword(), bean.getDatabase(), ip_node).getCouchDbClient();
        assertTrue(service2.context().getAllDbs().contains(DB+database)); // db is on second node

        ip_node.remove(0);
        ip_node.add(bean.getHosts().get(2));

        couchServ.createConnection(bean.getUsername(),bean.getPassword(), bean.getDatabase(), bean.getHosts());
        CouchDbClient service3 = couchServ.getCouchDbClient();
        //CouchDbClient service3 = conn.connection(bean.getUsername(), bean.getPassword(), bean.getDatabase(), ip_node).getCouchDbClient();
        assertTrue(service3.context().getAllDbs().contains(DB+database)); // db is on third node

        /* deleting ... */

        service2.context().deleteDB(DB+database, "delete database");
        assertFalse(service1.getCouchDbClient().context().getAllDbs().contains(DB+database));

    }

    @Test
    public void testB_provisionOnCluster() throws Exception {

        plan.setPlatform(Platform.EXISTING_SERVICE);
        plan.setId("1234-5678");
        service.createInstance(serviceInstance, plan, new HashMap<String, Object>());

        /* testing on all existing services */

        List<String> existingServices = new ArrayList<>();
        for (ServerAddress host : bean.getHosts()) {

            existingServices.add(host.getIp());
            log.info("checking for node at: "+existingServices.get(0));

            CouchDbClient client = conn.connection(serviceInstance, plan, true,null)
                                    .getCouchDbClient();

            assertNotNull(client.find(JsonObject.class, "org.couchdb.user:"+serviceInstance.getUsername())); // user created
            String uri = "http://"+bean.getUsername()+":"+bean.getPassword()+"@"+host.getIp()+":"+bean.getPort()+"/"+DB+serviceInstance.getId();

            HttpResponse response = performGet(uri);

            assertEquals(200, response.getStatusLine().getStatusCode()); // db found

            String uri_sec = uri+"/_security";
            HttpResponse resp = performGet(uri_sec);
            HttpEntity e = resp.getEntity();
            String ent = EntityUtils.toString(e);

            assertTrue(ent.contains("db-instance_binding")); // access protected to db
            client.shutdown();
            existingServices.remove(host);
        }
    }

    @Test
    public void testC_check_access_on_db() throws Exception {
        String userTest="userTest";
        String passwordTest="passwordTest";

        /* creation of another normal user in '/_users' db */

        CouchDbClient dbc = conn.connection(serviceInstance, plan, true,null)
                .getCouchDbClient();
        ArrayList<String> roles = new ArrayList<>();
        roles.add(serviceInstance.getId()+"_admin");
        UserDocument ud = new UserDocument("org.couchdb.user:"+userTest, userTest, passwordTest, new ArrayList<>(), "user");
        JsonObject js = (JsonObject)new Gson().toJsonTree(ud);
        dbc.save(js);
        /* check userTest cannot access db 'instance_binding' */

        String uri = "http://"+userTest+":"+passwordTest+"@"+bean.getHosts().get(0).getIp()+":"+bean.getPort()+"/"+DB+serviceInstance.getId();

        HttpResponse resp = performGet(uri);
        assertEquals(403, resp.getStatusLine().getStatusCode()); // "forbidden": Not allowed to access this db

        JsonObject j = dbc.find(JsonObject.class,"org.couchdb.user:userTest");
        dbc.remove(j);
        assertFalse(dbc.contains("org.couchdb.user:"+userTest));
    }

    @Test
    public void testD_check_update_security_document () throws Exception {
        String userTest="userTest";
        String passwordTest="passwordTest";

        CouchDbService to_users = conn.connection(serviceInstance, plan, true, null);
        /* giving access to db db-instance_binding */

        conn.bindRoleToInstanceWithPassword(to_users, DB+serviceInstance.getId(), userTest, passwordTest, plan);

        /* getting data ... */

        CouchDbClient dbc = to_users.getCouchDbClient();

        JsonObject j = dbc.find(JsonObject.class,"org.couchdb.user:userTest");
        assertTrue(dbc.contains("org.couchdb.user:"+userTest));


        CouchDbClient to_instance = conn.connection(serviceInstance, plan, true, null).getCouchDbClient();

        JsonObject jo = to_instance.find(JsonObject.class, "_security");
        String sec_doc = "{\"admins\":{\"names\":[\""+serviceInstance.getUsername()+"\"],\"roles\":[\"db-instance_binding_admin\"]},\"members\":{\"names\":[\""+serviceInstance.getUsername()+"\",\"userTest\"],\"roles\":[\"db-instance_binding_member\", \"db-instance_binding_member\"]}}";
        //assertEquals(sec_doc, jo.toString());

    }

    @Test
    public void testE_check_user_has_binding() throws Exception {

        String userTest="userTest";
        String passwordTest="passwordTest";

        CouchDbClient usr_db = conn.connection(serviceInstance, plan, true, null).getCouchDbClient();

        String uri = "http://"+userTest+":"+passwordTest+"@"+bean.getHosts().get(0).getIp()+":"+bean.getPort()+"/"+DB+serviceInstance.getId();

        HttpResponse resp = performGet(uri);
        assertEquals(200, resp.getStatusLine().getStatusCode()); // Now user can access db

        JsonObject j1 = usr_db.find(JsonObject.class,"org.couchdb.user:userTest");
        usr_db.remove(j1);
        assertFalse(usr_db.contains("org.couchdb.user:"+userTest));

    }

    @Test
    public void testF_bindingInstanceOnCluster() throws Exception {
        CouchDbService service1 = conn.connection(serviceInstance, plan, true, null);
        ServiceInstance si = new ServiceInstance("new-db", "sample-local", "1234-5678", "d", "d", new HashMap<>(), "d");
        plan.setPlatform(Platform.EXISTING_SERVICE);
        plan.setId("1234-5678");

        List<ServerAddress> list = new ArrayList<>();

        for (ServerAddress host : bean.getHosts()) {
            list.add(host);
        }

        couchService.createInstance(si, plan, si.getParameters());
        repository.addServiceInstance(si.getId(), si);
        assertNotNull(repository.getServiceInstance(si.getId()));

        String binding_id ="binding_id";

        ServiceInstanceBindingRequest request = new ServiceInstanceBindingRequest("sample-local", plan.getId());
        ServiceInstanceBindingResponse serviceInstanceBinding = bindingService.createServiceInstanceBinding(binding_id, si.getId(), request);

        //assertEquals(binding_id, serviceInstanceBinding.getCredentials().get("username"));

        String uri = "http://"+bean.getUsername()+":"+bean.getPassword()+"@"+bean.getHosts().get(0).getIp()+":"+bean.getPort()+"/"+DB+si.getId()+"/_security";


        HttpResponse r = performGet(uri);
        HttpEntity ee = r.getEntity();
        String entity = EntityUtils.toString(ee);
        //assertTrue(entity.contains("binding_id"));
    }

    @Test
    public void testG_deleting_instances () throws Exception {
        plan.setPlatform(Platform.EXISTING_SERVICE);
        String binding_id ="binding_id";
        ServiceInstanceBinding n = bindingRepository.findOne(binding_id);
        ServiceInstance s = repository.getServiceInstance("new-db");
        bindingService.deleteServiceInstanceBinding(binding_id, "1234-5678");
        deploymentService.syncDeleteInstance(s, plan, service);
        ServiceInstance s1 = repository.getServiceInstance("instance_binding");
        //deploymentService.syncDeleteInstance(s1, plan, service);
        CouchDbClient dbc =conn.connection(serviceInstance, plan, true, null).getCouchDbClient();
        dbc.context().deleteDB("db-instance_binding", "delete database");
        assertFalse(dbc.context().getAllDbs().contains("db-instance_binding"));
        assertFalse(dbc.contains("org.couchdb.user:"+n.getCredentials().get("username")));
        //assertFalse(dbc.contains("org.couchdb.user:"+s1.getUsername()));

    }
    public HttpResponse performGet (String uri) throws Exception {

        HttpClient c = new DefaultHttpClient();
        HttpGet get = new HttpGet(uri);
        return c.execute(get);
    }
}