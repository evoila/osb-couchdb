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
@ActiveProfiles(profiles={"singlenode"})
public class ConnectionTest {

    @Autowired
    private CouchDbCustomImplementation conn;

    @Autowired
    private CouchDbExistingServiceFactory couchService;

    @Autowired
    private ExistingEndpointBean bean;

    private ServiceInstance serviceInstance = new ServiceInstance("instance_binding", "9372FCCA-EC21-4EC5-B86B-B51E5D75DBET", "C433FC45-6404-433D-A5A5-F826817CF5BT", "d", "d", new HashMap<>(), "d");

    @Test
    public void testConnection () throws Exception {

        List<ServerAddress> hosts = bean.getHosts();
        String database = bean.getDatabase();
        String username = bean.getUsername();
        String password = bean.getPassword();


        Plan p = new Plan("433FC45-6404-433D-A5A5-F826817CF5BA", "name", "description", Platform.EXISTING_SERVICE, 10, VolumeUnit.G, "gp1.small", false, 40);
        conn.connection(serviceInstance, p, true, null);

        assertNotNull(conn.getService());
        assertTrue(conn.getService().isConnected());
        assertEquals(conn.getService().getCouchDbClient().getDBUri().toString(), "http://"+bean.getHosts().get(0).getIp()+":"+bean.getHosts().get(0).getPort()+"/"+bean.getDatabase()+"/");
    }
}
