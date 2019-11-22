package de.evoila.cf.cpi.bosh;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.model.catalog.plan.Plan;
import de.evoila.cf.broker.model.credential.UsernamePasswordCredential;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import de.evoila.cf.cpi.CredentialConstants;
import de.evoila.cf.cpi.bosh.deployment.manifest.Manifest;
import de.evoila.cf.security.credentials.CredentialStore;
import de.evoila.cf.security.utils.RandomString;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;

public class CouchDBDeploymentManager extends DeploymentManager {

    public static final String INSTANCE_GROUP = "couchdb";

    private RandomString randomStringUsername = new RandomString(10);

    private RandomString randomStringPassword = new RandomString(15);

    private CredentialStore credentialStore;

    public CouchDBDeploymentManager(BoshProperties boshProperties, Environment environment, CredentialStore credentialStore) {
        super(boshProperties, environment);
        this.credentialStore = credentialStore;
    }

    @Override
    protected void replaceParameters(ServiceInstance serviceInstance, Manifest manifest, Plan plan, Map<String, Object> customParameters, boolean isUpdate) {
        HashMap<String, Object> properties = new HashMap<>();
        if (customParameters != null && !customParameters.isEmpty())
            properties.putAll(customParameters);

        log.debug("Updating Deployment Manifest, replacing parameters");

        Map<String, Object> manifestProperties = manifest.getInstanceGroups()
                .stream()
                .filter(i -> i.getName().equals(INSTANCE_GROUP))
                .findAny().get().getProperties();

        UsernamePasswordCredential rootCredentials = credentialStore.createUser(serviceInstance,
                CredentialConstants.ROOT_CREDENTIALS, "root");

        Map<String, Object> couchdb = (Map<String, Object>) manifestProperties.get("couchdb");
        Map<String, Object> auth = (Map<String, Object>) couchdb.get("auth");

        auth.put("username", rootCredentials.getUsername());
        auth.put("password", rootCredentials.getPassword());

        serviceInstance.setUsername(rootCredentials.getUsername());
        serviceInstance.setPassword(rootCredentials.getPassword());

        this.updateInstanceGroupConfiguration(manifest, plan);
    }
}
