/**
 * 
 */
package de.evoila.cf.cpi.existing;

import com.google.gson.JsonObject;
import de.evoila.cf.broker.bean.impl.ExistingEndpointBeanImpl;
import de.evoila.cf.broker.exception.PlatformException;
import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.util.RandomString;
import de.evoila.cf.broker.custom.couchdb.CouchDbCustomImplementation;
import de.evoila.cf.broker.custom.couchdb.CouchDbService;
import org.lightcouch.CouchDbClient;
import org.lightcouch.CouchDbException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author Johannes Hiemer
 * @author Marco Di Martino
 *
 */
@Service
public class CouchDbExistingServiceFactory extends ExistingServiceFactory {

    //private static final String HTTP = "http://";
	private static final String PREFIX_ID = "org.couchdb.user:";
	private static final String DB = "db-";

	RandomString usernameRandomString = new RandomString(10);
	RandomString passwordRandomString = new RandomString(15);

	@Autowired
	private CouchDbCustomImplementation couchService;

    @Autowired
    private ExistingEndpointBeanImpl existingEndpointBean;

	@Override
	public ServiceInstance createInstance(ServiceInstance serviceInstance, Plan plan, Map<String, String> customParameters) throws PlatformException {
		String username = usernameRandomString.nextString();
		String password = passwordRandomString.nextString();

		serviceInstance.setUsername(username);
		serviceInstance.setPassword(password);

		CouchDbService couchDbService = connection(plan);
		String database = serviceInstance.getId();

		log.info("Creating the CouchDB Service...");
		database = DB + database;
		try {

			CouchDbClient client = couchDbService.getCouchDbClient();
			client.context().createDB(database);
		}catch (CouchDbException e){
			throw new PlatformException("Could not create to the database", e);
		}
		ArrayList<Object> admin_client = new ArrayList<>();
		CouchDbService clientToDatabase = connection(serviceInstance, plan);
		admin_client.add(clientToDatabase.getCouchDbClient());
		admin_client.add(existingEndpointBean.getPassword());
		try {
			CouchDbCustomImplementation.bindRoleToDatabaseWithPassword(couchDbService,
					database, username, password,true, admin_client);
		} catch(java.lang.Exception ex) {
			throw new PlatformException(ex);
		}

		return serviceInstance;
	}

	@Override
	public void deleteInstance(ServiceInstance serviceInstance, Plan plan) throws PlatformException {
		log.info("Deleting the CouchDB Service...");
		String database = serviceInstance.getId();
		CouchDbService couchDbService = connection(plan);

		database = DB + database;
		try{
			couchDbService.getCouchDbClient().context().deleteDB(database, "delete database");
			JsonObject user = couchDbService.getCouchDbClient().find(JsonObject.class, PREFIX_ID+serviceInstance.getUsername());
			couchDbService.getCouchDbClient().remove(user);
		}catch(CouchDbException e) {
			throw new PlatformException("could not delete from the database", e);
		}
	}

	private CouchDbService connection(Plan plan) {
		CouchDbService couchDbService = new CouchDbService();

		if (plan.getPlatform() == Platform.EXISTING_SERVICE)
			couchDbService.createConnection(existingEndpointBean.getHosts().get(0).getIp() , existingEndpointBean.getPort(), existingEndpointBean.getDatabase(),
					existingEndpointBean.getUsername(), existingEndpointBean.getPassword());
		return couchDbService;
	}
	private CouchDbService connection(ServiceInstance serviceInstance, Plan plan) {
		CouchDbService couchDbService = new CouchDbService();

		if (plan.getPlatform() == Platform.EXISTING_SERVICE)
			couchDbService.createConnection(existingEndpointBean.getHosts().get(0).getIp() , existingEndpointBean.getPort(), DB+serviceInstance.getId(),
					existingEndpointBean.getUsername(), existingEndpointBean.getPassword());
		return couchDbService;
	}
}