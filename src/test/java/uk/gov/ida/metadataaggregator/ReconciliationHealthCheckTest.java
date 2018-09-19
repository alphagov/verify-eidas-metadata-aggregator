package uk.gov.ida.metadataaggregator;

import com.codahale.metrics.health.HealthCheck;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ida.metadataaggregator.configuration.MetadataSourceConfiguration;
import uk.gov.ida.metadataaggregator.core.DecodingResults;
import uk.gov.ida.metadataaggregator.core.S3BucketMetadataStore;
import uk.gov.ida.metadataaggregator.exceptions.MetadataStoreException;
import uk.gov.ida.metadataaggregator.healthcheck.ReconciliationHealthCheck;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReconciliationHealthCheckTest {

    private ReconciliationHealthCheck reconciliationHealthCheck;
    private final S3BucketMetadataStore metadataStore = mock(S3BucketMetadataStore.class);
    private final MetadataSourceConfiguration config = mock(MetadataSourceConfiguration.class);
    private final static String BUCKET_URL_A = "http://localhost-country-a";
    private final static String BUCKET_URL_B = "http://localhost-country-b";
    private final static String BUCKET_URL_C = "http://localhost-country-c";
    private final static String INVALID_STRING = "Thisisinvalidencoding";

    private URL countryAConfigUrl;
    private URL countryBConfigUrl;
    private Map <String, URL> configUrls;

    @Before
    public void setUp() throws MalformedURLException {
        configUrls = new HashMap<>();
        countryAConfigUrl = new URL(BUCKET_URL_A);
        countryBConfigUrl = new URL(BUCKET_URL_B);
        reconciliationHealthCheck = new ReconciliationHealthCheck(metadataStore, config);
    }

    @Test
    public void shouldReturnHealthyWhenMetadataMatches() throws MetadataStoreException {
        configUrls.put("someCountry", countryAConfigUrl);

        when(config.getMetadataUrls()).thenReturn(configUrls);
        when(metadataStore.getAllUrls())
                .thenReturn(new DecodingResults(singletonList(BUCKET_URL_A), emptyList()));

        HealthCheck.Result check = reconciliationHealthCheck.check();

        assertTrue(check.isHealthy());
    }

    @Test
    public void shouldReturnHealthyWhenBothConfigAndBucketAreEmpty() throws MetadataStoreException {
        when(config.getMetadataUrls()).thenReturn(configUrls);
        when(metadataStore.getAllUrls()).thenReturn(new DecodingResults(emptyList(), emptyList()));

        HealthCheck.Result check = reconciliationHealthCheck.check();

        assertTrue(check.isHealthy());
    }

    @Test
    public void shouldReturnUnhealthyWhenMetadataIsNotInBucket() throws MetadataStoreException {
        configUrls.put("someCountry", countryAConfigUrl);

        when(config.getMetadataUrls()).thenReturn(configUrls);
        when(metadataStore.getAllUrls()).thenReturn(new DecodingResults(emptyList(), emptyList()));

        HealthCheck.Result check = reconciliationHealthCheck.check();

        String metadataConfigHealthCheckUrl = check.getDetails().get("inConfigNotInBucket").toString();

        assertFalse(check.isHealthy());
        assertTrue(metadataConfigHealthCheckUrl.contains(countryAConfigUrl.toString()));
    }

    @Test
    public void shouldReturnUnhealthyWhenMetadataIsNotInConfig() throws MetadataStoreException {
        when(config.getMetadataUrls()).thenReturn(configUrls);
        when(metadataStore.getAllUrls())
                .thenReturn(new DecodingResults(singletonList(BUCKET_URL_A), emptyList()));

        HealthCheck.Result check = reconciliationHealthCheck.check();

        String metadataBucketHealthCheckUrl = check.getDetails().get("inBucketNotInConfig").toString();

        assertFalse(check.isHealthy());
        assertTrue(metadataBucketHealthCheckUrl.contains(BUCKET_URL_A));

    }

    @Test
    public void shouldReturnUnhealthyWhenMetadataIsNotInConfigOrNotInBucket() throws MetadataStoreException {
        configUrls.put("countryA", countryAConfigUrl);
        configUrls.put("countryB", countryBConfigUrl);

        when(config.getMetadataUrls()).thenReturn(configUrls);
        when(metadataStore.getAllUrls())
                .thenReturn(new DecodingResults(emptyList(), asList(BUCKET_URL_A, BUCKET_URL_C)));

        HealthCheck.Result check = reconciliationHealthCheck.check();

        assertFalse(check.isHealthy());
    }

    @Test
    public void shouldReturnUnhealthyWhenUrlsHaveInvalidEncoding() throws MetadataStoreException {
        when(config.getMetadataUrls()).thenReturn(configUrls);
        when(metadataStore.getAllUrls())
                .thenReturn(new DecodingResults(emptyList(), singletonList(INVALID_STRING)));

        HealthCheck.Result check = reconciliationHealthCheck.check();

        String metadataBucketHealthCheckUrl = check.getDetails().get("invalidHexEncodedUrl").toString();

        assertFalse(check.isHealthy());
        assertThat(metadataBucketHealthCheckUrl).contains(INVALID_STRING);

    }
}
