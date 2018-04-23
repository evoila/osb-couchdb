import de.evoila.Application;
import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.model.*;
import de.evoila.cf.broker.custom.couchdb.CouchDbCustomImplementation;
import de.evoila.cf.cpi.existing.CouchDbExistingServiceFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.*;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * @author Marco Di Martino
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = Application.class)
@ContextConfiguration(classes = Application.class, loader = AnnotationConfigContextLoader.class, initializers = ConfigFileApplicationContextInitializer.class)
@ActiveProfiles(profiles={"default", "singlenode"})
public class ConnectionTest {

    @Autowired
    private CouchDbCustomImplementation conn;

    @Autowired
    private CouchDbExistingServiceFactory couchService;

    @Autowired
    private ExistingEndpointBean bean;

    private ServiceInstance serviceInstance = new ServiceInstance("instance_binding", "service_def", "s", "d", "d", new HashMap<>(), "d");

    @Test
    public void testConnection () throws Exception {

        int port = bean.getPort();
        List<ServerAddress> hosts = bean.getHosts();
        String database = bean.getDatabase();
        String username = bean.getUsername();
        String password = bean.getPassword();


        Plan p = new Plan();
        conn.connection(username, password, database, hosts);

        assertNotNull(conn.getService());
        assertTrue(conn.getService().isConnected());
        assertEquals(conn.getService().getCouchDbClient().getDBUri().toString(), "http://"+bean.getHosts().get(0).getIp()+":"+port+"/"+bean.getDatabase()+"/");
    }
}
