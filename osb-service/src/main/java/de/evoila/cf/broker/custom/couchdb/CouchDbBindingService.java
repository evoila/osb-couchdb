/**
 * 
 */
package de.evoila.cf.broker.custom.couchdb;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.evoila.cf.broker.bean.impl.ExistingEndpointBeanImpl;
import de.evoila.cf.broker.model.*;
import de.evoila.cf.broker.util.RandomString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.service.impl.BindingServiceImpl;

/**
 * @author Johannes Hiemer.
 * @author Marco Di Martino
 *
 */
@Service
public class CouchDbBindingService extends BindingServiceImpl {

	private Logger log = LoggerFactory.getLogger(getClass());

	private SecureRandom random = new SecureRandom();

    private static final String DB = "db-";

    RandomString usernameRandomString = new RandomString(10);
    RandomString passwordRandomString = new RandomString(15);

	@Autowired
	private ExistingEndpointBeanImpl endpointBean;

	@Override
	protected Map<String, Object> createCredentials(String bindingId, ServiceInstanceBindingRequest serviceInstanceBindingRequest, ServiceInstance serviceInstance,
			                                        Plan plan, ServerAddress host) throws ServiceBrokerException {

		log.info("Binding the CouchDB Service...");

        CouchDbService service = connection(plan);

        String username = usernameRandomString.nextString();
        String password = passwordRandomString.nextString();

        String database = DB+serviceInstance.getId();

        CouchDbService admin_to_db = connection(serviceInstance, plan);

        ArrayList<Object > adminPass = new ArrayList<Object>(){{
            add(admin_to_db.getCouchDbClient());
            add(endpointBean.getPassword());
        }};
        try {
            CouchDbCustomImplementation.bindRoleToDatabaseWithPassword(service, database, username, password, false, adminPass);
        }catch(Exception e){
            throw new ServiceBrokerException("Error while binding role", e);
        }

        Map<String, Object> credentials = new HashMap<>();

        credentials.put("username", username);
        credentials.put("password", password);
        credentials.put("database", database);

//		credentials.put("uri", dbURL);

        /*String dbURL = String.format("couchdb://%s:%s@%s:%d/%s", username,
                password, host.getName(), host.getPort(),
                database);//serviceInstance.getId());
        */
        return credentials;
    }

	@Override
	public void deleteBinding(ServiceInstanceBinding serviceInstanceBinding, ServiceInstance serviceInstance, Plan plan) throws ServiceBrokerException {

		log.info("Unbinding the CouchDB Service...");
        String bindingId = (String)serviceInstanceBinding.getCredentials().get("username");
		CouchDbService service = connection(plan);

		JsonObject toRemove = service.getCouchDbClient().find(JsonObject.class, "org.couchdb.user:"+bindingId);
		service.getCouchDbClient().remove(toRemove);
        String db = DB+serviceInstance.getId();
		service = connection(serviceInstance, plan);
        JsonObject security_doc = service.getCouchDbClient().find(JsonObject.class, "_security");
        SecurityDocument sd = new Gson().fromJson(security_doc, SecurityDocument.class);
        sd.getAdmins().deleteName(bindingId);
        sd.getMembers().deleteName(bindingId);

        JsonObject security = (JsonObject)new Gson().toJsonTree(sd);
        try {
            CouchDbCustomImplementation.send_put(service, db, serviceInstance.getUsername(),
                    serviceInstance.getPassword(), security.toString());
        }catch(Exception e){
            throw new ServiceBrokerException("An error has occurred while deleting binding", e);
        }
	}

	@Override
	public ServiceInstanceBinding getServiceInstanceBinding(String id) {
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * de.evoila.cf.broker.service.impl.BindingServiceImpl#bindRoute(de.evoila.
	 * cf.broker.model.ServiceInstance, java.lang.String)
	 */
	@Override
	protected RouteBinding bindRoute(ServiceInstance serviceInstance, String route) {
		throw new UnsupportedOperationException();
	}
	
    public String nextSessionId() {
        return new BigInteger(130, random).toString(32);
    }

    private CouchDbService connection(Plan plan) {
        CouchDbService couchDbService = new CouchDbService();

        if (plan.getPlatform() == Platform.EXISTING_SERVICE)
            couchDbService.createConnection(endpointBean.getHosts().get(0).getIp(), endpointBean.getPort(), endpointBean.getDatabase(),
                    endpointBean.getUsername(), endpointBean.getPassword());
        return couchDbService;
    }

    private CouchDbService connection(ServiceInstance serviceInstance, Plan plan) {
        CouchDbService couchDbService = new CouchDbService();

        if (plan.getPlatform() == Platform.EXISTING_SERVICE)
            couchDbService.createConnection(endpointBean.getHosts().get(0).getIp(), endpointBean.getPort(), DB+serviceInstance.getId(),
                    serviceInstance.getUsername(), serviceInstance.getPassword());
        return couchDbService;
    }

}