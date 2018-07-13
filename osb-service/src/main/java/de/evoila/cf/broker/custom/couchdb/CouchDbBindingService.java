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
import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.exception.InvalidParametersException;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.*;
import de.evoila.cf.broker.persistence.repository.ServiceDefinitionRepositoryImpl;
import de.evoila.cf.broker.util.RandomString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.service.impl.BindingServiceImpl;

import static de.evoila.cf.broker.model.Platform.BOSH;

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

    @Autowired
    private ExistingEndpointBean existingEndpointBean;

    @Autowired
    private ServiceDefinitionRepositoryImpl serviceDefinitionRepository;

    RandomString usernameRandomString = new RandomString(10);
    RandomString passwordRandomString = new RandomString(15);

    @Autowired
    private CouchDbCustomImplementation couchDbCustomImplementation;

	@Override
	protected Map<String, Object> createCredentials(String bindingId, ServiceInstanceBindingRequest serviceInstanceBindingRequest, ServiceInstance serviceInstance,
			                                        Plan plan, ServerAddress host) throws ServiceBrokerException, InvalidParametersException {

		log.info("Binding the CouchDB Service...");

        Map<String, Object> credentials = new HashMap<>();
        CouchDbService service = couchDbCustomImplementation.connection(serviceInstance, plan, true, null);
        String username = usernameRandomString.nextString();
        String password = passwordRandomString.nextString();

        if (plan.getPlatform() == Platform.EXISTING_SERVICE) {
            String database = DB + serviceInstance.getId();
            try {
                couchDbCustomImplementation.bindRoleToDatabaseWithPassword(service, database, username, password, plan);
            } catch (Exception e) {
                throw new ServiceBrokerException("Error while creating binding user", e);
            }
            CouchDbService service2 = couchDbCustomImplementation.connection(serviceInstance, plan, false, database);
            try{
                couchDbCustomImplementation.bindRole(service2, database, username, serviceInstance.getPassword(), plan);
            }catch(Exception e){
                throw new ServiceBrokerException("Error while binding role to the database", e);
            }

            credentials = new HashMap<>();

            credentials.put("username", username);
            credentials.put("password", password);
            credentials.put("database", database);

        }else if (plan.getPlatform() == BOSH){
            String database = null;
            HashMap<String, Object> parameters = (HashMap<String, Object>)serviceInstanceBindingRequest.getParameters();
            if (parameters == null || parameters.size() <= 0){
                database = "default";
                try {
                    couchDbCustomImplementation.bindRoleToDatabaseWithPassword(service, database, username, password, plan);
                } catch (Exception e) {
                    throw new ServiceBrokerException("Error while creating binding user", e);
                }
                CouchDbService service2 = couchDbCustomImplementation.connection(serviceInstance, plan, true, database);
                try{
                    couchDbCustomImplementation.bindRole(service2, database, username, serviceInstance.getPassword(), plan);
                }catch(Exception e){
                    throw new ServiceBrokerException("Error while binding role to the database", e);
                }

            }else{
                database = (String)parameters.get("database");
                try {
                    couchDbCustomImplementation.bindRoleToDatabaseWithPassword(service, database, username, serviceInstance.getPassword(), plan);
                } catch (Exception e) {
                    throw new ServiceBrokerException("Error while creating binding user", e);
                }
                CouchDbService service2 = couchDbCustomImplementation.connection(serviceInstance, plan, true, database);
                try {
                    couchDbCustomImplementation.bindRole(service2, database, username, serviceInstance.getPassword(), plan);
                } catch (Exception e) {
                    throw new ServiceBrokerException("Error while creating binding user", e);
                }
            }
            credentials = new HashMap<>();

            credentials.put("username", username);
            credentials.put("password", password);
            credentials.put("database", database);
        }

        return credentials;
    }

	@Override
	public void deleteBinding(ServiceInstanceBinding serviceInstanceBinding, ServiceInstance serviceInstance, Plan plan) throws ServiceBrokerException {

        log.info("Unbinding the CouchDB Service...");
        String bindingId = (String) serviceInstanceBinding.getCredentials().get("username");
        CouchDbService service = couchDbCustomImplementation.connection(serviceInstance, plan, true, null);

        JsonObject toRemove = service.getCouchDbClient().find(JsonObject.class, "org.couchdb.user:" + bindingId);
        service.getCouchDbClient().remove(toRemove);
        String database = null;
        // need to open a connection on the database
        if (plan.getPlatform() == Platform.BOSH) {
            // delete binding as Server Admin
            database = (String) serviceInstanceBinding.getCredentials().get("database");
            service = couchDbCustomImplementation.connection(serviceInstance, plan, true, database);
            JsonObject security_doc = service.getCouchDbClient().find(JsonObject.class, "_security");
            SecurityDocument sd = new Gson().fromJson(security_doc, SecurityDocument.class);
            sd.getAdmins().deleteName(bindingId);

            JsonObject security = (JsonObject) new Gson().toJsonTree(sd);
            try {
                couchDbCustomImplementation.sendPut(service, database, serviceInstance.getUsername(),
                        serviceInstance.getPassword(), security.toString());
            } catch (Exception e) {
                throw new ServiceBrokerException("An error has occurred while deleting binding", e);
            }
        }else if (plan.getPlatform() == Platform.EXISTING_SERVICE) {
            // delete binding as Database-Admin
            database = DB + serviceInstance.getId();
            service = couchDbCustomImplementation.connection(serviceInstance, plan, false, database);
            JsonObject security_doc = service.getCouchDbClient().find(JsonObject.class, "_security");
            SecurityDocument sd = new Gson().fromJson(security_doc, SecurityDocument.class);
            sd.getAdmins().deleteName(bindingId);

            JsonObject security = (JsonObject) new Gson().toJsonTree(sd);
            try {
                couchDbCustomImplementation.sendPut(service, database, serviceInstance.getUsername(),
                    serviceInstance.getPassword(), security.toString());
            } catch (Exception e) {
                throw new ServiceBrokerException("An error has occurred while deleting binding", e);
            }
        }
	}

	// probably
    private String getDatabaseForBinding(ServiceInstanceBindingRequest serviceInstanceBindingRequest, Plan plan) {return "";}

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

}