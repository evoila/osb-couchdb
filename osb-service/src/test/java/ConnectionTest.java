import de.evoila.Application;
import de.evoila.cf.broker.model.catalog.Catalog;
import de.evoila.cf.broker.service.impl.CatalogServiceImpl;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Marco Di Martino
 */

@RunWith(SpringRunner.class)
@ActiveProfiles({"local", "test"})
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
public class ConnectionTest{

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogServiceImpl catalogService;

    @Test
    public void testAgainstCatalog () throws Exception {

        given(catalogService.getCatalog()).willReturn(new Catalog());

        this.mockMvc.perform(get("/v2/catalog")
                .header("X-Broker-API-Version", "2.13")
                .with(httpBasic("admin", "cloudfoundry")))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
    }
}
