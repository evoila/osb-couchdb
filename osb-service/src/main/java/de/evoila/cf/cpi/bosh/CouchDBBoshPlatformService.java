package de.evoila.cf.cpi.bosh;

import de.evoila.cf.broker.bean.BoshProperties;
import de.evoila.cf.broker.custom.couchdb.CouchDbCustomImplementation;
import de.evoila.cf.broker.custom.couchdb.CouchDbService;
import de.evoila.cf.broker.model.DashboardClient;
import de.evoila.cf.broker.model.Plan;
import de.evoila.cf.broker.model.ServiceInstance;
import de.evoila.cf.broker.repository.PlatformRepository;
import de.evoila.cf.broker.service.CatalogService;
import de.evoila.cf.broker.service.availability.ServicePortAvailabilityVerifier;
import de.evoila.cf.cpi.bosh.deployment.DeploymentManager;
import io.bosh.client.deployments.Deployment;
import io.bosh.client.vms.Vm;
import org.lightcouch.CouchDbClient;
import org.lightcouch.CouchDbException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@ConditionalOnBean(BoshProperties.class)
public class CouchDBBoshPlatformService extends BoshPlatformService {

    private final int defaultPort = 5984;

    private CouchDbCustomImplementation couchDbCustomImplementation;

    public CouchDBBoshPlatformService(CouchDbCustomImplementation couchDbCustomImplementation,PlatformRepository repository, CatalogService catalogService,
                                      ServicePortAvailabilityVerifier availabilityVerifier, BoshProperties boshProperties, Optional<DashboardClient> dashboardClient,
                                      DeploymentManager deploymentManager) {
        super(repository, catalogService, availabilityVerifier, boshProperties, dashboardClient, deploymentManager);
        this.couchDbCustomImplementation = couchDbCustomImplementation;
    }

    CouchDBBoshPlatformService(PlatformRepository repository, CatalogService catalogService, ServicePortAvailabilityVerifier availabilityVerifier, BoshProperties boshProperties, Optional<DashboardClient> dashboardClient, Environment environment) {
        super(repository, catalogService,
              availabilityVerifier, boshProperties,
              dashboardClient, new CouchDBDeploymentManager(boshProperties, environment));
    }

    @Override
    protected void updateHosts(ServiceInstance serviceInstance, Plan plan, Deployment deployment) {
        List<Vm> vms = super.getVms(serviceInstance);
        serviceInstance.getHosts().clear();

        vms.forEach(vm -> serviceInstance.getHosts().add(super.toServerAddress(vm, defaultPort)));

        createDefaultDatabase(serviceInstance, plan);

    }

    @Override
    public void postDeleteInstance(ServiceInstance serviceInstance) { }

    private void createDefaultDatabase(ServiceInstance serviceInstance, Plan plan) {

        CouchDbService couchDbService = couchDbCustomImplementation.connection(serviceInstance, plan, true, null);
        String database = "default";
        log.info("Creating the default Database ...");
        try {
            CouchDbClient client = couchDbService.getCouchDbClient();
            client.context().createDB(database);
        }catch (CouchDbException e){
            throw new CouchDbException("Error during default database creation", e);
        }

    }
}
