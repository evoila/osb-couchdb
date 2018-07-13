/**
 * 
 */
package de.evoila.cf.broker.custom.couchdb;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import de.evoila.cf.broker.bean.impl.ExistingEndpointBeanImpl;
import de.evoila.cf.broker.exception.ServiceBrokerException;
import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.Platform;
import de.evoila.cf.broker.model.ServerAddress;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.util.ServiceInstanceUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.lightcouch.CouchDbClient;
import org.lightcouch.CouchDbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Johannes Hiemer
 * @author Marco Di Martino
 */

@Service
public class CouchDbCustomImplementation {

    private static final String APPLICATION_JSON = "application/json";

    private static final String CONTENT_TYPE = "Content-Type";

    private static final String PREFIX_ID = "org.couchdb.user:";

	private static final String DB = "db-";

	private CouchDbService couchDbService;
	private static final Logger log = LoggerFactory.getLogger(CouchDbCustomImplementation.class);

	@Autowired
	private ExistingEndpointBeanImpl existingEndpointBean;

	public CouchDbService connection(ServiceInstance serviceInstance, Plan plan, boolean isAdmin, String database) {
	    couchDbService = new CouchDbService();

	    if(plan.getPlatform() == Platform.BOSH) {
            List<ServerAddress> serverAddresses = serviceInstance.getHosts();

            if (plan.getMetadata().getIngressInstanceGroup() != null &&
            		plan.getMetadata().getIngressInstanceGroup().length() > 0) {
				serverAddresses = ServiceInstanceUtils.filteredServerAddress(serviceInstance.getHosts(),
						plan.getMetadata().getIngressInstanceGroup());
			}
			if (database == null) {
				couchDbService.createConnection(serviceInstance.getUsername(),
						serviceInstance.getPassword(), "_users", serverAddresses);
			}else{
				couchDbService.createConnection(serviceInstance.getUsername(),
						serviceInstance.getPassword(), database, serverAddresses);
			}
        } else if (plan.getPlatform() == Platform.EXISTING_SERVICE){
	    	// have to handle with different connections types
	        if (database == null) { // connect to _users
				if (isAdmin) { // as server Admin
					couchDbService.createConnection(existingEndpointBean.getUsername(),
							existingEndpointBean.getPassword(), existingEndpointBean.getDatabase(), existingEndpointBean.getHosts());
				} else { // as db admin
					couchDbService.createConnection(serviceInstance.getUsername(),
							serviceInstance.getPassword(), database, existingEndpointBean.getHosts());
				}
			}else{ // connect to specified database
	        	if (isAdmin){
					couchDbService.createConnection(existingEndpointBean.getUsername(),
							existingEndpointBean.getPassword(), database, existingEndpointBean.getHosts());
				}else{
					couchDbService.createConnection(serviceInstance.getUsername(),
							serviceInstance.getPassword(), database, existingEndpointBean.getHosts());
				}
			}
	    }
        return couchDbService;
	}

	public static void bindRoleToDatabaseWithPassword(CouchDbService connection, String database, String username, String password, Plan plan) throws Exception {
		String role = database + "_admin";
		String id = PREFIX_ID + username;
		/*creation of the user in the _user database
		* only server admin can access this database */

		ArrayList<String> user_roles = new ArrayList<>();
		user_roles.add(role);

		UserDocument userDoc = new UserDocument(id, username, password, user_roles, "user");
		Gson gson = new Gson();
		JsonObject js = (JsonObject) gson.toJsonTree(userDoc);
		connection.getCouchDbClient().save(js);

    }

    public void bindRole(CouchDbService connection, String database, String bindingName, String password, Plan plan) throws Exception {
		/* ** Security document **
		 * limit access to the database only for the created user
		 * Need to connect to the database as server admin to make changes to the _security document
		 * Cannot retrieve admin password from the configuration client (connection.getConfig().getPassword()==null)
		 * cannot retrieve endpointBean from static-context. Need to have variables from parameters
		*/
		String role = database + "_admin";
		JsonObject securityDocument = connection.getCouchDbClient().find(JsonObject.class, "_security");
		String username = connection.getConfig().getUsername();
		Gson gson = new Gson();
		SecurityDocument sec_doc = null;

		if (securityDocument.size() == 0) {
			/* create document, there are no specific database users
			   at document creation, only a database admin is created
			 */
			ArrayList<String> admin_names = new ArrayList<>();
			admin_names.add(bindingName);
			ArrayList<String> admin_roles = new ArrayList<>();
			admin_roles.add(role);

			NamesAndRoles adm = new NamesAndRoles(admin_names, admin_roles);
			sec_doc = new SecurityDocument(adm);

		} else {
			//update document
			sec_doc = gson.fromJson(securityDocument, SecurityDocument.class);
			sec_doc.getAdmins().addName(bindingName);
			sec_doc.getAdmins().getRoles().add(role);
		}

		JsonObject security = (JsonObject) gson.toJsonTree(sec_doc);

		sendPut(connection, database, username, password, security.toString());

	}

    /* must implement a Preemptive Basic Authentication */
	public void sendPut(CouchDbService connection, String database, String username,
                                   String password, String file_security) throws Exception {

		String host = connection.getConfig().getHost();
		String baseUri = connection.getCouchDbClient().getBaseUri().toString();
		String credentials = username+":"+password;
        String http = baseUri.substring(0,7);
		baseUri = baseUri.substring(7);

		String uri = http+credentials+"@"+baseUri+database+"/_security";


		HttpHost targetHost = new HttpHost(host, connection.getConfig().getPort(), "http");
		AuthCache authCache = new BasicAuthCache();
		authCache.put(targetHost, new BasicScheme());

		CredentialsProvider provider = new BasicCredentialsProvider();
		provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

		final HttpClientContext context = HttpClientContext.create();
		context.setCredentialsProvider(provider);
		context.setAuthCache(authCache);

		HttpClient client = HttpClientBuilder.create().setDefaultCredentialsProvider(provider).build();
		StringEntity params = new StringEntity(file_security, "UTF-8");
		HttpPut putRequest = new HttpPut(new URI(uri));

		params.setContentType(APPLICATION_JSON);
		putRequest.addHeader(CONTENT_TYPE,APPLICATION_JSON);
		putRequest.addHeader("Accept", APPLICATION_JSON);
		putRequest.setEntity(params);

		HttpResponse response = client.execute(putRequest, context);
        if (response.getStatusLine().getStatusCode() != 200){
            throw new Exception("Error while updating _security document");
        }
	}


	public void bindRoleToInstanceWithPassword(CouchDbService connection, String database,
			String username, String password, Plan plan) throws Exception {

			this.bindRoleToDatabaseWithPassword((CouchDbService) connection, database, username, password, plan);

	}

    public CouchDbService getService() {
		return couchDbService;
	}
}