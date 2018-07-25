/**
 *
 */
package de.evoila.cf.broker.custom.couchdb;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.evoila.cf.broker.bean.ExistingEndpointBean;
import de.evoila.cf.broker.exception.InvalidParametersException;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.*;
import de.evoila.cf.broker.persistence.repository.ServiceDefinitionRepositoryImpl;
import de.evoila.cf.broker.util.RandomString;
import de.evoila.cf.broker.util.ServiceInstanceUtils;
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

    private static String URI = "uri";
    private static String USER = "user";
    private static String DATABASE = "database";
    private static String NAME = "name";

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

        CouchDbService service = couchDbCustomImplementation.connection(serviceInstance, plan, true, null);
        String username = usernameRandomString.nextString();
        String password = passwordRandomString.nextString();

        String database = null;

        if (plan.getPlatform() == Platform.EXISTING_SERVICE) {
            database = DB + serviceInstance.getId();
            try {
                couchDbCustomImplementation.bindRoleToDatabaseWithPassword(service, database, username, password, plan);
            } catch (Exception e) {
                throw new ServiceBrokerException("Error while creating binding user", e);
            }
            CouchDbService service2 = couchDbCustomImplementation.connection(serviceInstance, plan, false, database);
            try {
                couchDbCustomImplementation.bindRole(service2, database, username, serviceInstance.getPassword());
            } catch (Exception e) {
                throw new ServiceBrokerException("Error while binding role to the database", e);
            }
        } else if (plan.getPlatform() == Platform.BOSH) {
            HashMap<String, Object> parameters = (HashMap<String, Object>) serviceInstanceBindingRequest.getParameters();
            if (parameters == null || parameters.size() <= 0) {
                database = "default";
                try {
                    couchDbCustomImplementation.bindRoleToDatabaseWithPassword(service, database, username, password, plan);
                } catch (Exception e) {
                    throw new ServiceBrokerException("Error while creating binding user", e);
                }
                CouchDbService service2 = couchDbCustomImplementation.connection(serviceInstance, plan, true, database);
                try {
                    couchDbCustomImplementation.bindRole(service2, database, username, serviceInstance.getPassword());
                } catch (Exception e) {
                    throw new ServiceBrokerException("Error while binding role to the database", e);
                }

            } else {
                database = (String) parameters.get("database");
                if (!(service.getCouchDbClient().context().getAllDbs().contains(database))){
                    throw new InvalidParametersException("The specified database for the binding does not exist");
                }
                // here should check if the database exists, and throw an Exception if it doesn't
                try {
                    couchDbCustomImplementation.bindRoleToDatabaseWithPassword(service, database, username, serviceInstance.getPassword(), plan);
                } catch (Exception e) {
                    throw new ServiceBrokerException("Error while creating binding user", e);
                }
                CouchDbService service2 = couchDbCustomImplementation.connection(serviceInstance, plan, true, database);
                try {
                    couchDbCustomImplementation.bindRole(service2, database, username, serviceInstance.getPassword());
                } catch (Exception e) {
                    throw new ServiceBrokerException("Error while creating binding user", e);
                }
            }
        }

        List<ServerAddress> serverAddresses = null;
        if (plan.getPlatform() == Platform.BOSH && plan.getMetadata() != null) {
            if (plan.getMetadata().getIngressInstanceGroup() != null && host == null)
                serverAddresses = ServiceInstanceUtils.filteredServerAddress(serviceInstance.getHosts(),
                        plan.getMetadata().getIngressInstanceGroup());
            else if (plan.getMetadata().getIngressInstanceGroup() == null)
                serverAddresses = serviceInstance.getHosts();
        } else if (plan.getPlatform() == Platform.EXISTING_SERVICE && existingEndpointBean != null) {
            serverAddresses = existingEndpointBean.getHosts();
        } else if (host != null)
            serverAddresses = Arrays.asList(new ServerAddress("service-key-haproxy", host.getIp(), host.getPort()));
        if (serverAddresses == null || serverAddresses.size() == 0)
            throw new ServiceBrokerException("Could not find any Service Backends to create Service Binding");
        String endpoint = ServiceInstanceUtils.connectionUrl(serverAddresses);
        // This needs to be done here and can't be generalized due to the fact that each backend
        // may have a different URL setup
        Map<String, Object> configurations = new HashMap<>();
        configurations.put(URI, String.format("couchdb://%s:%s@%s/%s", username, password, endpoint, database));
        configurations.put(DATABASE, database);
        configurations.put(NAME, database);
        Map<String, Object> credentials = ServiceInstanceUtils.bindingObject(serviceInstance.getHosts(),
                username,
                password,
                configurations);
        return credentials;
    }

	@Override
	public void unbindService(ServiceInstanceBinding serviceInstanceBinding, ServiceInstance serviceInstance, Plan plan) throws ServiceBrokerException {

        log.info("Unbinding the CouchDB Service...");
        String bindingId = (String) serviceInstanceBinding.getCredentials().get(USER);
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